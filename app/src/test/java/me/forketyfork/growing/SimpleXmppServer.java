package me.forketyfork.growing;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A minimal XMPP server that supports Smack 4.5+ authentication flow.
 * Supports SASL PLAIN authentication and basic IQ handling.
 * Uses streaming XML parser for performance and proper namespace handling.
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

            socket.setSoTimeout(30000); // 30-second timeout

            // Create XML factories for processing individual stanzas
            XMLInputFactory inputFactory = XMLInputFactory.newInstance();
            inputFactory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, Boolean.TRUE);
            inputFactory.setProperty(XMLInputFactory.IS_COALESCING, Boolean.TRUE);
            inputFactory.setProperty(XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);
            inputFactory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);

            boolean authenticated = false;

            // Main client handling loop (similar to the original but with streaming XML for stanzas)
            while (!socket.isClosed() && running.get()) {
                // Wait for stream header using original string-based approach
                if (!handleStreamHeader(in, out, authenticated)) {
                    break; // Connection closed or error
                }

                // Process stanzas until stream restart or connection close
                StringBuilder buffer = new StringBuilder();
                int ch;
                while ((ch = in.read()) != -1) {
                    buffer.append((char) ch);

                    String currentData = buffer.toString();

                    // Check for stream close
                    if (currentData.contains("</stream:stream>")) {
                        // Client is closing stream, send closing stream tag and break
                        out.write("</stream:stream>");
                        out.flush();
                        return; // End connection
                    }

                    // Check for stream restart
                    if (currentData.contains("<stream:stream") && buffer.length() > 50) {
                        // Stream restart detected, break to outer loop
                        break;
                    }

                    // Try to extract and process complete stanzas using streaming XML
                    String processedData = processStanzasWithXmlStreaming(buffer.toString(), out, inputFactory);
                    if (!processedData.contentEquals(buffer)) {
                        // Some stanzas were processed, check if we need to authenticate/restart
                        if (processedData.contains("SASL_SUCCESS")) {
                            authenticated = true;
                            buffer.setLength(0);
                            break; // Stream will restart
                        }
                        // Reset buffer with any remaining unprocessed data
                        buffer.setLength(0);
                        buffer.append(processedData);
                    }
                }

                if (ch == -1) break; // Connection closed
            }

        } catch (IOException ignored) {
            // Client disconnected or server stopping
        } finally {
            openClients.remove(socket);
        }
    }

    private boolean handleStreamHeader(BufferedReader in, BufferedWriter out, boolean authenticated) throws IOException {
        StringBuilder buffer = new StringBuilder();
        int ch;

        // Read until we get a stream header
        while ((ch = in.read()) != -1) {
            buffer.append((char) ch);
            if (buffer.toString().contains("<stream:stream")) {
                break;
            }
        }

        if (ch == -1) return false; // Connection closed

        // Send stream response
        String streamResponse = "<?xml version='1.0'?>" +
                "<stream:stream from='localhost' id='test-" + System.currentTimeMillis() +
                "' version='1.0' xmlns='jabber:client' xmlns:stream='http://etherx.jabber.org/streams'>";
        out.write(streamResponse);
        out.flush();

        // Send appropriate features
        String features = getFeatures(authenticated);

        out.write(features);
        out.flush();

        return true;
    }

    private static String getFeatures(boolean authenticated) {
        String features;
        if (!authenticated) {
            features = "<stream:features>" +
                    "<mechanisms xmlns='urn:ietf:params:xml:ns:xmpp-sasl'>" +
                    "<mechanism>PLAIN</mechanism>" +
                    "</mechanisms>" +
                    "</stream:features>";
        } else {
            features = "<stream:features>" +
                    "<compression xmlns='http://jabber.org/features/compress'>" +
                    "<method>zlib</method>" +
                    "</compression>" +
                    "<bind xmlns='urn:ietf:params:xml:ns:xmpp-bind'/>" +
                    "<session xmlns='urn:ietf:params:xml:ns:xmpp-session'/>" +
                    "</stream:features>";
        }
        return features;
    }

    private String processStanzasWithXmlStreaming(String data, BufferedWriter out, XMLInputFactory inputFactory) throws IOException {
        // Handle SASL auth
        String authStanza = extractCompleteStanza(data, "auth");
        if (authStanza != null) {
            try {
                XmlStanza stanza = parseStanzaFromString(authStanza, inputFactory);
                if ("urn:ietf:params:xml:ns:xmpp-sasl".equals(stanza.name.getNamespaceURI())) {
                    out.write("<success xmlns='urn:ietf:params:xml:ns:xmpp-sasl'/>");
                    out.flush();
                    return "SASL_SUCCESS"; // Special marker for stream restart
                }
            } catch (Exception e) {
                // Parsing failed, fall back to original behavior
            }
        }

        // Handle IQ stanzas
        String iqStanza = extractCompleteStanza(data, "iq");
        if (iqStanza != null) {
            try {
                // Convert to DOM for compatibility with existing IQ handling logic
                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                factory.setNamespaceAware(true);
                DocumentBuilder builder = factory.newDocumentBuilder();
                Document doc = builder.parse(new InputSource(new StringReader(iqStanza)));
                Element iqEl = doc.getDocumentElement();
                String response = handleIq(iqEl);
                if (response != null) {
                    out.write(response);
                    out.flush();
                }

                // Return remaining data after this stanza
                int startPos = data.indexOf(iqStanza);
                int endPos = startPos + iqStanza.length();
                String remaining = data.substring(0, startPos) + data.substring(endPos);
                return remaining.trim().isEmpty() ? "" : remaining;

            } catch (Exception e) {
                // Parsing failed, return original data
            }
        }

        // No complete stanzas found, return original data
        return data;
    }

    private String extractCompleteStanza(String data, String tagName) {
        // Look for the complete stanza using simple string searching
        int currentPos = 0;

        // Find opening tag
        while (currentPos < data.length()) {
            int tagStart = data.indexOf('<' + tagName + ' ', currentPos);
            int tagStartNoSpace = data.indexOf('<' + tagName + '>', currentPos);
            int tagStartSelfClose = data.indexOf('<' + tagName + "/>");

            if (tagStartSelfClose != -1 && (tagStart == -1 || tagStartSelfClose < tagStart) &&
                    (tagStartNoSpace == -1 || tagStartSelfClose < tagStartNoSpace)) {
                // Self-closing tag found
                int endPos = tagStartSelfClose + tagName.length() + 3;
                return data.substring(tagStartSelfClose, endPos);
            }

            int openStart = tagStart != -1 ? tagStart : tagStartNoSpace;
            if (openStart == -1) break;

            int openEnd = data.indexOf('>', openStart);
            if (openEnd == -1) break;

            // Look for the closing tag
            String closeTag = "</" + tagName + ">";
            int closePos = data.indexOf(closeTag, openEnd);
            if (closePos == -1) {
                // Try with the namespace prefix (simplified)
                String nsClosePattern = ":" + tagName + ">";
                int nsClosePos = data.indexOf(nsClosePattern, openEnd);
                if (nsClosePos != -1) {
                    // Find the actual start of the closing tag
                    int nsCloseStart = data.lastIndexOf('<', nsClosePos);
                    if (nsCloseStart != -1) {
                        int nsCloseEnd = data.indexOf('>', nsClosePos);
                        if (nsCloseEnd != -1) {
                            return data.substring(openStart, nsCloseEnd + 1);
                        }
                    }
                }
                break;
            } else {
                return data.substring(openStart, closePos + closeTag.length());
            }
        }

        return null;
    }

    private XmlStanza parseStanzaFromString(String xml, XMLInputFactory inputFactory) throws XMLStreamException {
        XMLStreamReader xmlReader = inputFactory.createXMLStreamReader(new StringReader(xml));

        // Move to the start element
        while (xmlReader.hasNext() && xmlReader.getEventType() != XMLStreamConstants.START_ELEMENT) {
            xmlReader.next();
        }

        if (xmlReader.getEventType() == XMLStreamConstants.START_ELEMENT) {
            return parseStanza(xmlReader, xmlReader.getName());
        }

        throw new XMLStreamException("No start element found");
    }

    private String handleIq(Element iq) {
        String type = iq.getAttribute("type");
        String id = iq.getAttribute("id");
        if (id == null || id.isEmpty()) id = "response";

        // Look for queries
        NodeList queries = iq.getElementsByTagNameNS("*", "query");
        if (queries.getLength() > 0) {
            Element query = (Element) queries.item(0);
            String ns = query.getNamespaceURI();

            if ("jabber:iq:auth".equals(ns)) {
                if ("get".equals(type)) {
                    return "<iq type='result' id='" + id + "'>" +
                            "<query xmlns='jabber:iq:auth'><username/><password/><resource/></query>" +
                            "</iq>";
                } else if ("set".equals(type)) {
                    return "<iq type='result' id='" + id + "'/>";
                }
            } else if ("jabber:iq:roster".equals(ns) && "get".equals(type)) {
                return "<iq type='result' id='" + id + "'><query xmlns='jabber:iq:roster'/></iq>";
            }
        }

        // Look for bind
        NodeList binds = iq.getElementsByTagNameNS("urn:ietf:params:xml:ns:xmpp-bind", "bind");
        if (binds.getLength() > 0 && "set".equals(type)) {
            return "<iq type='result' id='" + id + "'>" +
                    "<bind xmlns='urn:ietf:params:xml:ns:xmpp-bind'>" +
                    "<jid>test@localhost/resource</jid>" +
                    "</bind></iq>";
        }

        // Look for session
        NodeList sessions = iq.getElementsByTagNameNS("urn:ietf:params:xml:ns:xmpp-session", "session");
        if (sessions.getLength() > 0 && "set".equals(type)) {
            return "<iq type='result' id='" + id + "'/>";
        }

        // Default response for get queries
        if ("get".equals(type)) {
            return "<iq type='result' id='" + id + "'/>";
        }

        return null;
    }

    // Data class to hold parsed stanza information
    private record XmlStanza(QName name, Map<String, String> attributes, String textContent, List<Element> children) {
    }

    private XmlStanza parseStanza(XMLStreamReader xmlIn, QName elementName) throws XMLStreamException {
        java.util.Map<String, String> attributes = new java.util.HashMap<>();

        // Read attributes
        for (int i = 0; i < xmlIn.getAttributeCount(); i++) {
            attributes.put(xmlIn.getAttributeLocalName(i), xmlIn.getAttributeValue(i));
        }

        StringBuilder textContent = new StringBuilder();
        java.util.List<Element> children = new java.util.ArrayList<>();

        while (xmlIn.hasNext()) {
            int event = xmlIn.next();

            if (event == XMLStreamConstants.CHARACTERS) {
                textContent.append(xmlIn.getText());
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                QName endName = xmlIn.getName();
                if (endName.equals(elementName)) {
                    break;
                }
            }
        }

        return new XmlStanza(elementName, attributes, textContent.toString().trim(), children);
    }

}