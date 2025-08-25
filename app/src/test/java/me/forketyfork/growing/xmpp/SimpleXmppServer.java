package me.forketyfork.growing.xmpp;

import javax.xml.namespace.QName;
import javax.xml.stream.*;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A minimal XMPP server that supports Smack 4.5+ authentication flow.
 * Supports SASL PLAIN authentication and basic IQ handling.
 * Uses streaming XML parser/writer for proper event-driven XML processing.
 */
public class SimpleXmppServer {

    private final Logger logger = Logger.getLogger("SimpleXmppServer");

    private final XmppServerConfig config;
    private ServerSocket serverSocket;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicInteger activeConnections = new AtomicInteger(0);
    private final CountDownLatch shutdownLatch = new CountDownLatch(1);

    private final ExecutorService acceptor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "SimpleXmppServer-Acceptor");
        t.setDaemon(true);
        return t;
    });
    private final ExecutorService clients = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "SimpleXmppServer-Client");
        t.setDaemon(true);
        return t;
    });

    private final Set<Socket> openClients = Collections.synchronizedSet(new HashSet<>());

    // Handler interfaces for extensibility
    private final XmppStreamHandler streamHandler;
    private final XmppSaslHandler saslHandler;
    private final XmppIqHandler iqHandler;
    private final XmppMessageHandler messageHandler;

    public SimpleXmppServer(int port) {
        this(new XmppServerConfig(port));
    }

    public SimpleXmppServer(XmppServerConfig config) {
        this.config = config;
        this.streamHandler = new DefaultStreamHandler(config);
        this.saslHandler = new DefaultSaslHandler();
        this.iqHandler = new DefaultIqHandler();
        this.messageHandler = new DefaultMessageHandler();
    }

    public void start() throws IOException {
        if (running.getAndSet(true)) return;
        serverSocket = new ServerSocket(config.port());
        acceptor.submit(() -> {
            while (running.get()) {
                try {
                    Socket socket = serverSocket.accept();

                    // Check connection limit
                    if (activeConnections.get() >= config.maxConnections()) {
                        logger.log(Level.WARNING, "Connection limit reached, rejecting client");
                        socket.close();
                        continue;
                    }

                    openClients.add(socket);
                    activeConnections.incrementAndGet();
                    clients.submit(() -> handleClient(socket));
                } catch (IOException e) {
                    if (running.get()) {
                        logger.log(Level.WARNING, "IOException occurred during socket processing", e);
                    }
                }
            }
        });
    }

    public void stop() {
        if (!running.getAndSet(false)) return;

        logger.info("Stopping XMPP server, active connections: " + activeConnections.get());

        // Close a server socket to stop accepting new connections
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (IOException e) {
            logger.log(Level.WARNING, "IOException occurred on closing the server socket", e);
        }

        // Graceful shutdown: wait for active connections to complete or timeout
        long startTime = System.currentTimeMillis();
        final long shutdownPollIntervalMs = 100L;
        while (activeConnections.get() > 0 &&
                (System.currentTimeMillis() - startTime) < config.shutdownTimeoutMs()) {
            try {
                Thread.sleep(shutdownPollIntervalMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        // Force close remaining connections
        synchronized (openClients) {
            for (Socket s : openClients) {
                try {
                    s.close();
                } catch (IOException e) {
                    logger.log(Level.WARNING, "IOException occurred on closing the open clients", e);
                }
            }
            openClients.clear();
        }

        // Shutdown thread pools
        acceptor.shutdownNow();
        clients.shutdownNow();

        // Wait for thread pools to terminate
        try {
            if (!acceptor.awaitTermination(config.shutdownTimeoutMs(), TimeUnit.MILLISECONDS)) {
                logger.warning("Failed to await for termination of socket acceptor");
            }
            if (!clients.awaitTermination(config.shutdownTimeoutMs(), TimeUnit.MILLISECONDS)) {
                logger.warning("Failed to await for termination of clients");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        shutdownLatch.countDown();
        logger.info("XMPP server stopped");
    }

    private void handleClient(Socket socket) {
        logger.info("Client connected");
        try (socket) {
            socket.setSoTimeout(config.socketTimeoutMs());

            // Create an XML input factory and output factory
            XMLInputFactory inputFactory = XMLInputFactory.newInstance();
            inputFactory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, Boolean.TRUE);
            inputFactory.setProperty(XMLInputFactory.IS_COALESCING, Boolean.TRUE);
            inputFactory.setProperty(XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);
            inputFactory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);

            XMLOutputFactory outputFactory = XMLOutputFactory.newInstance();

            // Create a streaming XML reader and writer for this client
            try (InputStreamReader inputStreamReader = new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8);
                 OutputStreamWriter outputStreamWriter = new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8)) {

                XMLStreamReader xmlReader = inputFactory.createXMLStreamReader(inputStreamReader);
                XMLStreamWriter xmlWriter = outputFactory.createXMLStreamWriter(outputStreamWriter);

                try {
                    ClientState state = ClientState.WAITING_FOR_STREAM_START;

                    // Event-driven XML processing loop
                    while (!socket.isClosed() && running.get() && state != ClientState.CLOSED) {
                        try {
                            if (!xmlReader.hasNext()) {
                                break;
                            }

                            int event = xmlReader.next();
                            state = processXmlEvent(xmlReader, xmlWriter, event, state);

                        } catch (XMLStreamException e) {
                            // Connection closed or malformed XML
                            logger.log(Level.FINE, "XML parsing error, closing connection", e);
                            break;
                        }
                    }
                } finally {
                    try {
                        if (xmlWriter != null) {
                            xmlWriter.close();
                        }
                    } catch (XMLStreamException e) {
                        logger.log(Level.FINE, "Error closing XML writer", e);
                    }
                    try {
                        if (xmlReader != null) {
                            xmlReader.close();
                        }
                    } catch (XMLStreamException e) {
                        logger.log(Level.FINE, "Error closing XML reader", e);
                    }
                }
            }

        } catch (IOException e) {
            if (running.get()) {
                logger.log(Level.FINE, "IOException handling client connection", e);
            }
            // Otherwise, expected during shutdown
        } catch (XMLStreamException e) {
            logger.log(Level.FINE, "XMLStreamException handling client connection", e);
        } finally {
            openClients.remove(socket);
            activeConnections.decrementAndGet();
            logger.info("Client disconnected, active connections: " + activeConnections.get());
        }
    }

    private String getXmlEventName(int event) {
        return switch (event) {
            case XMLStreamConstants.START_ELEMENT -> "START_ELEMENT";
            case XMLStreamConstants.END_ELEMENT -> "END_ELEMENT";
            case XMLStreamConstants.PROCESSING_INSTRUCTION -> "PROCESSING_INSTRUCTION";
            case XMLStreamConstants.CHARACTERS -> "CHARACTERS";
            case XMLStreamConstants.COMMENT -> "COMMENT";
            case XMLStreamConstants.SPACE -> "SPACE";
            case XMLStreamConstants.START_DOCUMENT -> "START_DOCUMENT";
            case XMLStreamConstants.END_DOCUMENT -> "END_DOCUMENT";
            case XMLStreamConstants.ENTITY_REFERENCE -> "ENTITY_REFERENCE";
            case XMLStreamConstants.ATTRIBUTE -> "ATTRIBUTE";
            case XMLStreamConstants.DTD -> "DTD";
            case XMLStreamConstants.CDATA -> "CDATA";
            case XMLStreamConstants.NAMESPACE -> "NAMESPACE";
            case XMLStreamConstants.NOTATION_DECLARATION -> "NOTATION_DECLARATION";
            case XMLStreamConstants.ENTITY_DECLARATION -> "ENTITY_DECLARATION";
            default -> "UNKNOWN_EVENT_TYPE";
        };
    }

    private ClientState processXmlEvent(XMLStreamReader xmlReader, XMLStreamWriter xmlWriter,
                                        int event, ClientState currentState) throws XMLStreamException {

        logger.log(Level.FINE, "XML Event: {0}, currentState: {1}", new Object[]{getXmlEventName(event), currentState});

        return switch (event) {
            case XMLStreamConstants.START_ELEMENT -> handleStartElement(xmlReader, xmlWriter, currentState);
            case XMLStreamConstants.END_ELEMENT -> handleEndElement(xmlReader, xmlWriter, currentState);
            default -> currentState;
        };
    }

    private ClientState handleStartElement(XMLStreamReader xmlReader, XMLStreamWriter xmlWriter,
                                           ClientState currentState) throws XMLStreamException {
        QName elementName = xmlReader.getName();
        logger.log(Level.FINE, "Handling start element: {0}, currentState: {1}", new Object[]{elementName, currentState});
        String localName = elementName.getLocalPart();
        String namespace = elementName.getNamespaceURI();

        // Handle stream start
        if ("stream".equals(localName) && XmppServerConfig.NAMESPACE_STREAM.equals(namespace)) {
            return streamHandler.handleStreamStart(xmlReader, xmlWriter, currentState);
        }

        // Handle authentication
        if ("auth".equals(localName) && XmppServerConfig.NAMESPACE_SASL.equals(namespace)) {
            return saslHandler.handleSaslAuth(xmlReader, xmlWriter, currentState);
        }

        // Handle IQ stanzas
        if ("iq".equals(localName) && XmppServerConfig.NAMESPACE_CLIENT.equals(namespace)) {
            return iqHandler.handleIqStanza(xmlReader, xmlWriter, currentState);
        }

        // Handle message stanzas
        if ("message".equals(localName) && XmppServerConfig.NAMESPACE_CLIENT.equals(namespace)) {
            return messageHandler.handleMessageStanza(xmlReader, xmlWriter, currentState);
        }

        return currentState;
    }

    private ClientState handleEndElement(XMLStreamReader xmlReader, XMLStreamWriter xmlWriter,
                                         ClientState currentState) throws XMLStreamException {
        QName elementName = xmlReader.getName();
        logger.log(Level.FINE, "Handling end element: {0}, currentState: {1}", new Object[]{elementName, currentState});

        String localName = elementName.getLocalPart();
        String namespace = elementName.getNamespaceURI();

        // Handle stream end
        if ("stream".equals(localName) && XmppServerConfig.NAMESPACE_STREAM.equals(namespace)) {
            return streamHandler.handleStreamEnd(xmlReader, xmlWriter, currentState);
        }

        return currentState;
    }


}