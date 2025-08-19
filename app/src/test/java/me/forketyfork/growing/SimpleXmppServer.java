package me.forketyfork.growing;

import javax.xml.namespace.QName;
import javax.xml.stream.*;
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
 * Uses StAX streaming parser for proper namespace-aware XML processing.
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

    private record XmppRequest(Type type, XmppStanza stanza) {
        enum Type {STREAM_START, SASL_AUTH, IQ, STREAM_END}
    }

    private record XmppResponse(ResponseType type, XmppStanza stanza, boolean authenticate, boolean closeConnection) {
        enum ResponseType {STREAM_START, SASL_SUCCESS, IQ_RESULT, STREAM_END}

        boolean shouldAuthenticate() {
            return authenticate;
        }

        boolean shouldCloseConnection() {
            return closeConnection;
        }
    }

    private static class XmppStanza {
        private final QName name;
        private final java.util.Map<String, String> attributes;
        private final String textContent;
        private final java.util.List<XmppStanza> children;
        private final String namespace;

        public XmppStanza(QName name, java.util.Map<String, String> attributes) {
            this(name, attributes, null, new java.util.ArrayList<>());
        }

        public XmppStanza(QName name, java.util.Map<String, String> attributes, String textContent) {
            this(name, attributes, textContent, new java.util.ArrayList<>());
        }

        public XmppStanza(QName name, java.util.Map<String, String> attributes, String textContent, java.util.List<XmppStanza> children) {
            this.name = name;
            this.attributes = attributes != null ? new java.util.HashMap<>(attributes) : new java.util.HashMap<>();
            this.textContent = textContent;
            this.children = children != null ? new java.util.ArrayList<>(children) : new java.util.ArrayList<>();
            this.namespace = name.getNamespaceURI();
        }

        public QName getName() {
            return name;
        }

        public java.util.Map<String, String> getAttributes() {
            return attributes;
        }

        public String getTextContent() {
            return textContent;
        }

        public java.util.List<XmppStanza> getChildren() {
            return children;
        }

        public String getNamespace() {
            return namespace;
        }

        public String getAttribute(String name) {
            return attributes.get(name);
        }

    }

    private XmppRequest parseRequest(BufferedReader in) throws IOException {
        StringBuilder buffer = new StringBuilder();
        int ch;

        while ((ch = in.read()) != -1) {
            buffer.append((char) ch);
            String data = buffer.toString();

            // Check for stream end (namespace-aware)
            if (data.matches(".*</[^:]*:?stream>.*")) {
                return new XmppRequest(XmppRequest.Type.STREAM_END, null);
            }

            // Check for stream start (namespace-aware)
            if (data.matches(".*<[^:]*:?stream[^>]*>.*")) {
                return new XmppRequest(XmppRequest.Type.STREAM_START, null);
            }

            // Try to parse complete stanzas using namespace-aware approach
            try {
                XmppStanza stanza = extractAndParseStanza(data, "auth");
                if (stanza != null && isNamespaceMatch(stanza.getNamespace(), "urn:ietf:params:xml:ns:xmpp-sasl")) {
                    return new XmppRequest(XmppRequest.Type.SASL_AUTH, stanza);
                }

                stanza = extractAndParseStanza(data, "iq");
                if (stanza != null && (isNamespaceMatch(stanza.getNamespace(), "jabber:client") ||
                        stanza.getNamespace() == null || stanza.getNamespace().isEmpty())) {
                    return new XmppRequest(XmppRequest.Type.IQ, stanza);
                }
            } catch (Exception e) {
                // Continue reading
            }
        }

        return null; // Connection closed
    }

    private XmppStanza extractAndParseStanza(String data, String tagName) throws XMLStreamException {
        // Look for complete stanza (with closing tag or self-closed)
        String stanzaXml = extractCompleteStanza(data, tagName);
        if (stanzaXml == null) return null;

        return parseStanzaXml(stanzaXml);
    }

    private String extractCompleteStanza(String data, String tagName) {
        // Look for the opening tag (with any namespace prefix)
        java.util.regex.Pattern openPattern = java.util.regex.Pattern.compile("<(?:[^:]+:)?" + tagName + "[^>]*>");
        java.util.regex.Matcher openMatcher = openPattern.matcher(data);

        if (!openMatcher.find()) return null;

        int start = openMatcher.start();
        String openTag = openMatcher.group();

        // Check if it's self-closed
        if (openTag.endsWith("/>")) {
            return openTag;
        }

        // Look for closing tag
        java.util.regex.Pattern closePattern = java.util.regex.Pattern.compile("</(?:[^:]+:)?" + tagName + ">");
        java.util.regex.Matcher closeMatcher = closePattern.matcher(data);

        if (closeMatcher.find(start)) {
            return data.substring(start, closeMatcher.end());
        }

        return null;
    }

    private XmppStanza parseStanzaXml(String xml) throws XMLStreamException {
        XMLInputFactory factory = XMLInputFactory.newInstance();
        factory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, Boolean.TRUE);

        XMLStreamReader reader = factory.createXMLStreamReader(new java.io.StringReader(xml));

        // Move to start element
        while (reader.hasNext() && reader.getEventType() != XMLStreamConstants.START_ELEMENT) {
            reader.next();
        }

        if (reader.getEventType() == XMLStreamConstants.START_ELEMENT) {
            return parseStanzaFromReader(reader);
        }

        return null;
    }

    private XmppStanza parseStanzaFromReader(XMLStreamReader reader) throws XMLStreamException {
        QName elementName = reader.getName();
        java.util.Map<String, String> attributes = new java.util.HashMap<>();

        // Read attributes
        for (int i = 0; i < reader.getAttributeCount(); i++) {
            attributes.put(reader.getAttributeLocalName(i), reader.getAttributeValue(i));
        }

        java.util.List<XmppStanza> children = new java.util.ArrayList<>();
        StringBuilder textContent = new StringBuilder();

        while (reader.hasNext()) {
            int event = reader.next();

            if (event == XMLStreamConstants.START_ELEMENT) {
                XmppStanza child = parseStanzaFromReader(reader);
                children.add(child);
            } else if (event == XMLStreamConstants.CHARACTERS) {
                textContent.append(reader.getText());
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                QName endName = reader.getName();
                if (endName.equals(elementName)) {
                    break;
                }
            }
        }

        String content = textContent.toString().trim();
        return new XmppStanza(elementName, attributes, content.isEmpty() ? null : content, children);
    }

    private boolean isNamespaceMatch(String actualNs, String expectedNs) {
        if (actualNs == null) actualNs = "";
        if (expectedNs == null) expectedNs = "";
        return actualNs.equals(expectedNs);
    }

    private XmppStanza createFeatures(boolean authenticated) {
        QName featuresName = new QName("http://etherx.jabber.org/streams", "features");
        java.util.List<XmppStanza> children = new java.util.ArrayList<>();

        if (!authenticated) {
            // SASL mechanisms
            QName mechanismsName = new QName("urn:ietf:params:xml:ns:xmpp-sasl", "mechanisms");
            QName mechanismName = new QName("urn:ietf:params:xml:ns:xmpp-sasl", "mechanism");
            XmppStanza mechanism = new XmppStanza(mechanismName, new java.util.HashMap<>(), "PLAIN");
            XmppStanza mechanisms = new XmppStanza(mechanismsName, new java.util.HashMap<>(), null, java.util.List.of(mechanism));
            children.add(mechanisms);
        } else {
            // Compression feature
            QName compressionName = new QName("http://jabber.org/features/compress", "compression");
            QName methodName = new QName("http://jabber.org/features/compress", "method");
            XmppStanza method = new XmppStanza(methodName, new java.util.HashMap<>(), "zlib");
            XmppStanza compression = new XmppStanza(compressionName, new java.util.HashMap<>(), null, java.util.List.of(method));
            children.add(compression);

            // Bind feature
            QName bindName = new QName("urn:ietf:params:xml:ns:xmpp-bind", "bind");
            XmppStanza bind = new XmppStanza(bindName, new java.util.HashMap<>());
            children.add(bind);

            // Session feature
            QName sessionName = new QName("urn:ietf:params:xml:ns:xmpp-session", "session");
            XmppStanza session = new XmppStanza(sessionName, new java.util.HashMap<>());
            children.add(session);
        }

        return new XmppStanza(featuresName, new java.util.HashMap<>(), null, children);
    }

    private XmppResponse handleRequest(XmppRequest request, boolean authenticated) {
        return switch (request.type) {
            case STREAM_START -> createStreamResponse(authenticated);
            case SASL_AUTH -> createSaslResponse();
            case IQ -> createIqResponse(request.stanza);
            case STREAM_END -> createStreamEndResponse();
        };
    }

    private XmppResponse createStreamResponse(boolean authenticated) {
        XmppStanza features = createFeatures(authenticated);
        return new XmppResponse(XmppResponse.ResponseType.STREAM_START, features, false, false);
    }

    private XmppResponse createSaslResponse() {
        QName successName = new QName("urn:ietf:params:xml:ns:xmpp-sasl", "success");
        XmppStanza success = new XmppStanza(successName, new java.util.HashMap<>());
        return new XmppResponse(XmppResponse.ResponseType.SASL_SUCCESS, success, true, false);
    }

    private XmppResponse createIqResponse(XmppStanza iqStanza) {
        XmppStanza response = handleIqStanza(iqStanza);
        return new XmppResponse(XmppResponse.ResponseType.IQ_RESULT, response, false, false);
    }

    private XmppResponse createStreamEndResponse() {
        return new XmppResponse(XmppResponse.ResponseType.STREAM_END, null, false, true);
    }

    private void sendResponse(XmppResponse response, BufferedWriter out) throws IOException {
        if (response.stanza == null) {
            if (response.shouldCloseConnection()) {
                String closeXml = "</stream:stream>";
                out.write(closeXml);
                out.flush();
            }
            return;
        }

        try {
            StringWriter stringWriter = new StringWriter();
            XMLOutputFactory factory = XMLOutputFactory.newInstance();
            XMLStreamWriter writer = factory.createXMLStreamWriter(stringWriter);

            if (response.type == XmppResponse.ResponseType.STREAM_START) {
                // Send stream header
                String streamHeader = "<?xml version='1.0'?>" +
                        "<stream:stream" +
                        " from='localhost'" +
                        " id='test-" + System.currentTimeMillis() + "'" +
                        " version='1.0'" +
                        " xmlns='jabber:client'" +
                        " xmlns:stream='http://etherx.jabber.org/streams'>";
                out.write(streamHeader);

                // Send features - write directly as fragment, not as complete document
                try {
                    writeStanzaFragment(writer, response.stanza);
                    writer.flush();
                    String featuresXml = stringWriter.toString();
                    out.write(featuresXml);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else {
                // Regular stanza - write as fragment
                writeStanzaFragment(writer, response.stanza);
                String stanzaXml = stringWriter.toString();
                out.write(stanzaXml);
            }

            writer.close();
            out.flush();
        } catch (XMLStreamException e) {
            throw new IOException("Failed to write XML response", e);
        }
    }

    private XmppStanza handleIqStanza(XmppStanza iq) {
        String type = iq.getAttribute("type");
        String id = iq.getAttribute("id");
        if (id == null || id.isEmpty()) id = "response";

        java.util.Map<String, String> responseAttrs = new java.util.HashMap<>();
        responseAttrs.put("type", "result");
        responseAttrs.put("id", id);

        QName iqName = new QName("jabber:client", "iq");
        java.util.List<XmppStanza> responseChildren = new java.util.ArrayList<>();

        // Look for query elements
        for (XmppStanza child : iq.getChildren()) {
            if ("query".equals(child.getName().getLocalPart())) {
                String ns = child.getNamespace();

                if ("jabber:iq:auth".equals(ns) && "get".equals(type)) {
                    QName queryName = new QName("jabber:iq:auth", "query");
                    java.util.List<XmppStanza> queryChildren = java.util.List.of(
                            new XmppStanza(new QName("username"), new java.util.HashMap<>()),
                            new XmppStanza(new QName("password"), new java.util.HashMap<>()),
                            new XmppStanza(new QName("resource"), new java.util.HashMap<>())
                    );
                    responseChildren.add(new XmppStanza(queryName, new java.util.HashMap<>(), null, queryChildren));
                } else if ("jabber:iq:roster".equals(ns) && "get".equals(type)) {
                    QName queryName = new QName("jabber:iq:roster", "query");
                    responseChildren.add(new XmppStanza(queryName, new java.util.HashMap<>()));
                }
            } else if ("bind".equals(child.getName().getLocalPart()) &&
                    "urn:ietf:params:xml:ns:xmpp-bind".equals(child.getNamespace()) &&
                    "set".equals(type)) {
                QName bindName = new QName("urn:ietf:params:xml:ns:xmpp-bind", "bind");
                QName jidName = new QName("urn:ietf:params:xml:ns:xmpp-bind", "jid");
                XmppStanza jid = new XmppStanza(jidName, new java.util.HashMap<>(), "test@localhost/resource");
                responseChildren.add(new XmppStanza(bindName, new java.util.HashMap<>(), null, java.util.List.of(jid)));
            }
        }

        return new XmppStanza(iqName, responseAttrs, null, responseChildren);
    }

    private void writeStanzaFragment(XMLStreamWriter writer, XmppStanza stanza) throws XMLStreamException {
        // This writes just the stanza element without XML declaration
        writeStanzaElement(writer, stanza);
    }

    private void writeStanzaElement(XMLStreamWriter writer, XmppStanza stanza) throws XMLStreamException {
        QName name = stanza.getName();
        String namespace = name.getNamespaceURI();
        String localName = name.getLocalPart();
        String prefix = name.getPrefix();

        // Start element with namespace handling
        if (namespace != null && !namespace.isEmpty()) {
            if (prefix != null && !prefix.isEmpty()) {
                writer.writeStartElement(prefix, localName, namespace);
            } else {
                // Use default namespace
                writer.writeStartElement(localName);
                writer.writeDefaultNamespace(namespace);
            }
        } else {
            writer.writeStartElement(localName);
        }

        // Write namespace declarations if needed (only for prefixed namespaces)
        if (namespace != null && !namespace.isEmpty() && prefix != null && !prefix.isEmpty()) {
            writer.writeNamespace(prefix, namespace);
        }

        // Write attributes
        for (java.util.Map.Entry<String, String> attr : stanza.getAttributes().entrySet()) {
            writer.writeAttribute(attr.getKey(), attr.getValue());
        }

        // Write text content if present
        if (stanza.getTextContent() != null && !stanza.getTextContent().trim().isEmpty()) {
            writer.writeCharacters(stanza.getTextContent());
        }

        // Write children
        for (XmppStanza child : stanza.getChildren()) {
            writeStanzaElement(writer, child);
        }

        writer.writeEndElement();
    }

}