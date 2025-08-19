package me.forketyfork.growing;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A minimal XMPP server that supports Smack 4.5+ authentication flow.
 * Supports SASL PLAIN authentication and basic IQ handling.
 */
@SuppressWarnings("HttpUrlsUsage")
public class SimpleXmppServer {
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
                    e.printStackTrace();
                }
            }
        });
    }

    public void stop() {
        if (!running.getAndSet(false)) return;
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (IOException ignored) {
        }
        synchronized (openClients) {
            for (Socket s : openClients) {
                try {
                    s.close();
                } catch (IOException ignored) {
                }
            }
            openClients.clear();
        }
        acceptor.shutdownNow();
        clients.shutdownNow();
    }

    private void handleClient(Socket socket) {
        try (socket;
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))) {

            socket.setSoTimeout(30000);
            boolean authenticated = false;

            while (!socket.isClosed() && running.get()) {
                XmppRequest request = parseRequest(in);
                if (request == null) break; // Connection closed

                XmppResponse response = handleRequest(request, authenticated);
                if (response.shouldAuthenticate()) {
                    authenticated = true;
                }

                sendResponse(response, out);

                if (response.shouldCloseConnection()) {
                    break;
                }
            }

        } catch (IOException ignored) {
        } finally {
            openClients.remove(socket);
        }
    }

    private record XmppRequest(Type type, Element element) {
        enum Type {STREAM_START, SASL_AUTH, IQ, STREAM_END}
    }

    private record XmppResponse(Document document, boolean authenticate, boolean closeConnection) {

        boolean shouldAuthenticate() {
            return authenticate;
        }

        boolean shouldCloseConnection() {
            return closeConnection;
        }
    }

    private XmppRequest parseRequest(BufferedReader in) throws IOException {
        StringBuilder buffer = new StringBuilder();
        int ch;

        while ((ch = in.read()) != -1) {
            buffer.append((char) ch);
            String data = buffer.toString();

            if (data.contains("</stream:stream>")) {
                return new XmppRequest(XmppRequest.Type.STREAM_END, null);
            }

            if (data.contains("<stream:stream")) {
                return new XmppRequest(XmppRequest.Type.STREAM_START, null);
            }

            // Try to parse complete stanzas
            try {
                if (data.contains("<auth ")) {
                    String stanza = extractCompleteStanza(data, "auth");
                    if (stanza != null) {
                        Element element = parseStanza(stanza);
                        if (element != null) {
                            return new XmppRequest(XmppRequest.Type.SASL_AUTH, element);
                        }
                    }
                }

                if (data.contains("<iq ")) {
                    String stanza = extractCompleteStanza(data, "iq");
                    if (stanza != null) {
                        Element element = parseStanza(stanza);
                        if (element != null) {
                            return new XmppRequest(XmppRequest.Type.IQ, element);
                        }
                    }
                }
            } catch (Exception e) {
                // Continue reading
            }
        }

        return null; // Connection closed
    }

    private String extractCompleteStanza(String data, String tagName) {
        int start = data.indexOf("<" + tagName + " ");
        if (start == -1) return null;

        int end = data.indexOf("</" + tagName + ">", start);
        if (end != -1) {
            return data.substring(start, end + tagName.length() + 3);
        }

        end = data.indexOf("/>", start);
        if (end != -1) {
            return data.substring(start, end + 2);
        }

        return null;
    }

    private Element parseStanza(String stanza) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();

            // Suppress XML parsing error messages
            builder.setErrorHandler(new org.xml.sax.ErrorHandler() {
                public void warning(org.xml.sax.SAXParseException e) {
                }

                public void error(org.xml.sax.SAXParseException e) throws org.xml.sax.SAXException {
                    throw e;
                }

                public void fatalError(org.xml.sax.SAXParseException e) throws org.xml.sax.SAXException {
                    throw e;
                }
            });

            // Wrap the stanza in a root element to make it a valid XML document
            String wrappedStanza = "<root xmlns:stream='http://etherx.jabber.org/streams'>" + stanza + "</root>";
            Document doc = builder.parse(new InputSource(new StringReader(wrappedStanza)));

            // Return the first child (the actual stanza) instead of the wrapper
            return (Element) doc.getDocumentElement().getFirstChild();
        } catch (Exception e) {
            return null;
        }
    }

    private Element createFeatures(boolean authenticated) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            Document doc = factory.newDocumentBuilder().newDocument();
            Element features = doc.createElementNS("http://etherx.jabber.org/streams", "stream:features");

            if (!authenticated) {
                Element mechanisms = doc.createElement("mechanisms");
                mechanisms.setAttribute("xmlns", "urn:ietf:params:xml:ns:xmpp-sasl");

                Element mechanism = doc.createElement("mechanism");
                mechanism.setTextContent("PLAIN");
                mechanisms.appendChild(mechanism);

                features.appendChild(mechanisms);
            } else {
                Element compression = doc.createElement("compression");
                compression.setAttribute("xmlns", "http://jabber.org/features/compress");

                Element method = doc.createElement("method");
                method.setTextContent("zlib");
                compression.appendChild(method);

                features.appendChild(compression);

                Element bind = doc.createElement("bind");
                bind.setAttribute("xmlns", "urn:ietf:params:xml:ns:xmpp-bind");
                features.appendChild(bind);

                Element session = doc.createElement("session");
                session.setAttribute("xmlns", "urn:ietf:params:xml:ns:xmpp-session");
                features.appendChild(session);
            }

            return features;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create features", e);
        }
    }

    private XmppResponse handleRequest(XmppRequest request, boolean authenticated) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            Document responseDoc = factory.newDocumentBuilder().newDocument();

            return switch (request.type) {
                case STREAM_START -> createStreamResponse(responseDoc, authenticated);
                case SASL_AUTH -> createSaslResponse(responseDoc);
                case IQ -> createIqResponse(responseDoc, request.element);
                case STREAM_END -> createStreamEndResponse();
            };
        } catch (Exception e) {
            return new XmppResponse(null, false, true);
        }
    }

    private XmppResponse createStreamResponse(Document doc, boolean authenticated) {
        // Create a complete response with stream header and features
        Element root = doc.createElement("xmpp-response");
        doc.appendChild(root);

        // Add stream header info
        Element streamHeader = doc.createElement("stream-header");
        streamHeader.setAttribute("from", "localhost");
        streamHeader.setAttribute("id", "test-" + System.currentTimeMillis());
        streamHeader.setAttribute("version", "1.0");
        root.appendChild(streamHeader);

        // Add features
        Element features = createFeatures(authenticated);
        Element importedFeatures = (Element) doc.importNode(features, true);
        root.appendChild(importedFeatures);

        return new XmppResponse(doc, false, false);
    }

    private XmppResponse createSaslResponse(Document doc) {
        Element success = doc.createElementNS("urn:ietf:params:xml:ns:xmpp-sasl", "success");
        doc.appendChild(success);

        return new XmppResponse(doc, true, false);
    }

    private XmppResponse createIqResponse(Document doc, Element iqElement) {
        Element response = handleIqElement(iqElement);
        if (response != null) {
            Element importedResponse = (Element) doc.importNode(response, true);
            doc.appendChild(importedResponse);
            return new XmppResponse(doc, false, false);
        }
        return new XmppResponse(null, false, false);
    }

    private XmppResponse createStreamEndResponse() {
        // Just signal to close connection
        return new XmppResponse(null, false, true);
    }

    private void sendResponse(XmppResponse response, BufferedWriter out) throws IOException {
        if (response.document == null) {
            if (response.shouldCloseConnection()) {
                out.write("</stream:stream>");
                out.flush();
            }
            return;
        }

        Element root = response.document.getDocumentElement();

        if ("xmpp-response".equals(root.getTagName())) {
            // Stream response - handle specially
            Element streamHeader = (Element) root.getElementsByTagName("stream-header").item(0);
            Element features = (Element) root.getElementsByTagNameNS("http://etherx.jabber.org/streams", "features").item(0);

            String streamResponse = "<?xml version='1.0'?>" +
                    "<stream:stream" +
                    " from='" + streamHeader.getAttribute("from") + "'" +
                    " id='" + streamHeader.getAttribute("id") + "'" +
                    " version='" + streamHeader.getAttribute("version") + "'" +
                    " xmlns='jabber:client'" +
                    " xmlns:stream='http://etherx.jabber.org/streams'>" +
                    elementToString(features);

            out.write(streamResponse);
        } else {
            // Regular stanza
            out.write(elementToString(root));
        }

        out.flush();
    }

    private Element handleIqElement(Element iq) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            Document doc = factory.newDocumentBuilder().newDocument();
            String type = iq.getAttribute("type");
            String id = iq.getAttribute("id");
            if (id == null || id.isEmpty()) id = "response";

            Element response = doc.createElement("iq");
            response.setAttribute("type", "result");
            response.setAttribute("id", id);

            // Look for queries
            NodeList queries = iq.getElementsByTagNameNS("*", "query");
            if (queries.getLength() > 0) {
                Element query = (Element) queries.item(0);
                String ns = query.getNamespaceURI();

                if ("jabber:iq:auth".equals(ns) && "get".equals(type)) {
                    Element queryResp = doc.createElement("query");
                    queryResp.setAttribute("xmlns", "jabber:iq:auth");
                    queryResp.appendChild(doc.createElement("username"));
                    queryResp.appendChild(doc.createElement("password"));
                    queryResp.appendChild(doc.createElement("resource"));
                    response.appendChild(queryResp);
                } else if ("jabber:iq:roster".equals(ns) && "get".equals(type)) {
                    Element queryResp = doc.createElement("query");
                    queryResp.setAttribute("xmlns", "jabber:iq:roster");
                    response.appendChild(queryResp);
                }
            }

            // Look for bind
            NodeList binds = iq.getElementsByTagNameNS("urn:ietf:params:xml:ns:xmpp-bind", "bind");
            if (binds.getLength() > 0 && "set".equals(type)) {
                Element bindResp = doc.createElement("bind");
                bindResp.setAttribute("xmlns", "urn:ietf:params:xml:ns:xmpp-bind");

                Element jid = doc.createElement("jid");
                jid.setTextContent("test@localhost/resource");
                bindResp.appendChild(jid);

                response.appendChild(bindResp);
            }

            return response;
        } catch (Exception e) {
            return null;
        }
    }

    private String elementToString(Element element) {
        try {
            TransformerFactory tf = TransformerFactory.newInstance();
            Transformer transformer = tf.newTransformer();
            transformer.setOutputProperty("omit-xml-declaration", "yes");

            StringWriter writer = new StringWriter();
            transformer.transform(new DOMSource(element), new StreamResult(writer));
            return writer.toString();
        } catch (TransformerException e) {
            throw new RuntimeException("Failed to convert element to string", e);
        }
    }
}