package me.forketyfork.growing;

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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A minimal XMPP server that supports Smack 4.5+ authentication flow.
 * Supports SASL PLAIN authentication and basic IQ handling.
 * Uses streaming XML parser/writer for proper event-driven XML processing.
 */
@SuppressWarnings("HttpUrlsUsage")
public class SimpleXmppServer {

    private final Logger logger = Logger.getLogger("SimpleXmppServer");


    private final int port;
    private ServerSocket serverSocket;
    private final AtomicBoolean running = new AtomicBoolean(false);
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

    // Client state for XMPP flow
    private enum ClientState {
        WAITING_FOR_STREAM_START,
        WAITING_FOR_AUTH,
        AUTHENTICATED_WAITING_FOR_RESTART,
        PROCESSING_STANZAS,
        CLOSED
    }

    public SimpleXmppServer(int port) {
        this.port = port;
    }

    public void start() throws IOException {
        if (running.getAndSet(true)) return;
        serverSocket = new ServerSocket(port);
        acceptor.submit(() -> {
            while (running.get()) {
                try {
                    Socket socket = serverSocket.accept();
                    openClients.add(socket);
                    clients.submit(() -> handleClient(socket));
                } catch (IOException e) {
                    logger.log(Level.WARNING, "IOException occurred during socket processing", e);
                }
            }
        });
    }

    public void stop() {
        if (!running.getAndSet(false)) return;
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (IOException e) {
            logger.log(Level.WARNING, "IOException occurred on closing the server socket", e);
        }
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
        acceptor.shutdownNow();
        clients.shutdownNow();
    }

    private void handleClient(Socket socket) {
        logger.info("Client connected");
        try (socket) {
            socket.setSoTimeout(30000); // 30-second timeout

            // Create an XML input factory and output factory
            XMLInputFactory inputFactory = XMLInputFactory.newInstance();
            inputFactory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, Boolean.TRUE);
            inputFactory.setProperty(XMLInputFactory.IS_COALESCING, Boolean.TRUE);
            inputFactory.setProperty(XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);
            inputFactory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);

            XMLOutputFactory outputFactory = XMLOutputFactory.newInstance();

            // Create a streaming XML reader and writer for this client
            XMLStreamReader xmlReader = inputFactory.createXMLStreamReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            XMLStreamWriter xmlWriter = outputFactory.createXMLStreamWriter(
                    new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));

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
                    break;
                }
            }

            xmlWriter.close();
            xmlReader.close();

        } catch (IOException | XMLStreamException ignored) {
            // Client disconnected or server stopping
        } finally {
            openClients.remove(socket);
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
        if ("stream".equals(localName) && "http://etherx.jabber.org/streams".equals(namespace)) {
            return handleStreamStart(xmlWriter, currentState);
        }

        // Handle authentication
        if ("auth".equals(localName) && "urn:ietf:params:xml:ns:xmpp-sasl".equals(namespace)) {
            return handleSaslAuth(xmlReader, xmlWriter, currentState);
        }

        // Handle IQ stanzas
        if ("iq".equals(localName) && "jabber:client".equals(namespace)) {
            return handleIqStanza(xmlReader, xmlWriter, currentState);
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
        if ("stream".equals(localName) && "http://etherx.jabber.org/streams".equals(namespace)) {
            xmlWriter.writeEndElement(); // Close our stream
            xmlWriter.flush();
            return ClientState.CLOSED;
        }

        return currentState;
    }

    private ClientState handleStreamStart(XMLStreamWriter xmlWriter, ClientState currentState) throws XMLStreamException {
        logger.log(Level.FINE, "Stream start, currentState: {0}", currentState);

        // Send XML declaration and stream header
        xmlWriter.writeStartDocument("UTF-8", "1.0");
        xmlWriter.writeStartElement("stream", "stream", "http://etherx.jabber.org/streams");
        xmlWriter.writeAttribute("from", "localhost");
        xmlWriter.writeAttribute("id", "test-" + System.currentTimeMillis());
        xmlWriter.writeAttribute("version", "1.0");
        xmlWriter.writeDefaultNamespace("jabber:client");
        xmlWriter.writeNamespace("stream", "http://etherx.jabber.org/streams");

        // Send features based on the current state
        if (currentState == ClientState.WAITING_FOR_STREAM_START) {
            sendSaslFeatures(xmlWriter);
            return ClientState.WAITING_FOR_AUTH;
        } else if (currentState == ClientState.AUTHENTICATED_WAITING_FOR_RESTART) {
            sendBindFeatures(xmlWriter);
            return ClientState.PROCESSING_STANZAS;
        }

        xmlWriter.flush();
        return currentState;
    }

    private void sendSaslFeatures(XMLStreamWriter xmlWriter) throws XMLStreamException {
        xmlWriter.writeStartElement("stream", "features", "http://etherx.jabber.org/streams");
        xmlWriter.writeStartElement("mechanisms");
        xmlWriter.writeAttribute("xmlns", "urn:ietf:params:xml:ns:xmpp-sasl");
        xmlWriter.writeStartElement("mechanism");
        xmlWriter.writeCharacters("PLAIN");
        xmlWriter.writeEndElement(); // mechanism
        xmlWriter.writeEndElement(); // mechanisms
        xmlWriter.writeEndElement(); // features
        xmlWriter.flush();
    }

    private void sendBindFeatures(XMLStreamWriter xmlWriter) throws XMLStreamException {
        xmlWriter.writeStartElement("stream", "features", "http://etherx.jabber.org/streams");
        xmlWriter.writeStartElement("compression");
        xmlWriter.writeAttribute("xmlns", "http://jabber.org/features/compress");
        xmlWriter.writeStartElement("method");
        xmlWriter.writeCharacters("zlib");
        xmlWriter.writeEndElement(); // method
        xmlWriter.writeEndElement(); // compression
        xmlWriter.writeEmptyElement("bind");
        xmlWriter.writeAttribute("xmlns", "urn:ietf:params:xml:ns:xmpp-bind");
        xmlWriter.writeEmptyElement("session");
        xmlWriter.writeAttribute("xmlns", "urn:ietf:params:xml:ns:xmpp-session");
        xmlWriter.writeEndElement(); // features
        xmlWriter.flush();
    }

    private ClientState handleSaslAuth(XMLStreamReader xmlReader, XMLStreamWriter xmlWriter,
                                       ClientState currentState) throws XMLStreamException {
        logger.log(Level.FINE, "Handling SASL Auth, currentState: {0}", currentState);

        // Read the auth content (we don't need to validate it for this test server)
        StringBuilder authContent = new StringBuilder();
        while (xmlReader.hasNext()) {
            int event = xmlReader.next();
            if (event == XMLStreamConstants.CHARACTERS) {
                authContent.append(xmlReader.getText());
            } else if (event == XMLStreamConstants.END_ELEMENT &&
                    "auth".equals(xmlReader.getName().getLocalPart())) {
                break;
            }
        }

        logger.log(Level.FINE, "Received SASL Auth: {0}, sending auth success", authContent);

        // Send SASL success
        xmlWriter.writeStartElement("success");
        xmlWriter.writeAttribute("xmlns", "urn:ietf:params:xml:ns:xmpp-sasl");
        xmlWriter.writeEndElement();
        xmlWriter.flush();
        return ClientState.AUTHENTICATED_WAITING_FOR_RESTART;
    }

    private ClientState handleIqStanza(XMLStreamReader xmlReader, XMLStreamWriter xmlWriter,
                                       ClientState currentState) throws XMLStreamException {
        logger.log(Level.FINE, "Handling IQ Stanza, currentState: {0}", currentState);

        // Read IQ attributes
        String type = xmlReader.getAttributeValue(null, "type");
        String id = xmlReader.getAttributeValue(null, "id");
        if (id == null || id.isEmpty()) id = "response";

        // Parse the IQ content
        String queryNs = null;
        boolean hasBind = false;

        // Read the IQ content
        while (xmlReader.hasNext()) {
            int event = xmlReader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                QName elementName = xmlReader.getName();
                String localName = elementName.getLocalPart();
                String namespace = elementName.getNamespaceURI();

                if ("query".equals(localName)) {
                    queryNs = namespace;
                } else if ("bind".equals(localName) && "urn:ietf:params:xml:ns:xmpp-bind".equals(namespace)) {
                    hasBind = true;
                }
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                if ("iq".equals(xmlReader.getName().getLocalPart())) {
                    break;
                }
            }
        }

        // Generate response
        sendIqResponse(xmlWriter, type, id, queryNs, hasBind);
        return currentState;
    }

    private void sendIqResponse(XMLStreamWriter xmlWriter, String type, String id, String queryNs,
                                boolean hasBind) throws XMLStreamException {
        xmlWriter.writeStartElement("iq");
        xmlWriter.writeAttribute("type", "result");
        xmlWriter.writeAttribute("id", id);

        if (queryNs != null) {
            if ("jabber:iq:auth".equals(queryNs)) {
                if ("get".equals(type)) {
                    xmlWriter.writeStartElement("query");
                    xmlWriter.writeAttribute("xmlns", "jabber:iq:auth");
                    xmlWriter.writeEmptyElement("username");
                    xmlWriter.writeEmptyElement("password");
                    xmlWriter.writeEmptyElement("resource");
                    xmlWriter.writeEndElement(); // query
                }
            } else if ("jabber:iq:roster".equals(queryNs) && "get".equals(type)) {
                xmlWriter.writeEmptyElement("query");
                xmlWriter.writeAttribute("xmlns", "jabber:iq:roster");
            }
        } else if (hasBind) {
            xmlWriter.writeStartElement("bind");
            xmlWriter.writeAttribute("xmlns", "urn:ietf:params:xml:ns:xmpp-bind");
            xmlWriter.writeStartElement("jid");
            xmlWriter.writeCharacters("test@localhost/resource");
            xmlWriter.writeEndElement(); // jid
            xmlWriter.writeEndElement(); // bind
        }

        xmlWriter.writeEndElement(); // iq
        xmlWriter.flush();
    }

}