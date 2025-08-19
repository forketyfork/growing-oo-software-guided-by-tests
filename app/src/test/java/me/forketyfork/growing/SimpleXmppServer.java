package me.forketyfork.growing;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
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

            socket.setSoTimeout(30000); // 30 second timeout

            boolean authenticated = false;
            boolean bound = false;

            // Main client handling loop
            while (!socket.isClosed() && running.get()) {
                // Wait for stream header
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

                    // Try to extract and process complete stanzas
                    String processedData = processStanzas(buffer.toString(), out);
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

    private String processStanzas(String data, BufferedWriter out) throws IOException {
        // Look for complete stanzas (simple approach)

        // Handle SASL auth
        int authStart = data.indexOf("<auth ");
        if (authStart != -1) {
            int authEnd = data.indexOf("/>", authStart);
            if (authEnd == -1) {
                authEnd = data.indexOf("</auth>", authStart);
                if (authEnd != -1) authEnd += 6; // Include </auth>
            } else {
                authEnd += 1; // Include />
            }
            if (authEnd != -1) {
                // Complete auth stanza found
                out.write("<success xmlns='urn:ietf:params:xml:ns:xmpp-sasl'/>");
                out.flush();
                return "SASL_SUCCESS"; // Special marker for stream restart
            }
        }

        // Handle IQ stanzas
        int iqStart = data.indexOf("<iq ");
        if (iqStart != -1) {
            int iqEnd = data.indexOf("</iq>", iqStart);
            if (iqEnd == -1) {
                iqEnd = data.indexOf("/>", iqStart);
            }
            if (iqEnd != -1) {
                // Complete IQ stanza found
                try {
                    int endIndex = iqEnd + (data.charAt(iqEnd) == '/' ? 2 : 5);
                    String iqStanza = data.substring(iqStart, endIndex);
                    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                    factory.setNamespaceAware(true);
                    DocumentBuilder builder = factory.newDocumentBuilder();

                    // Set error handler to suppress XML parsing error messages
                    builder.setErrorHandler(new org.xml.sax.ErrorHandler() {
                        public void warning(org.xml.sax.SAXParseException e) { /* ignore */ }

                        public void error(org.xml.sax.SAXParseException e) throws org.xml.sax.SAXException {
                            throw e;
                        }

                        public void fatalError(org.xml.sax.SAXParseException e) throws org.xml.sax.SAXException {
                            throw e;
                        }
                    });

                    Document doc = builder.parse(new InputSource(new StringReader(iqStanza)));
                    Element iqEl = doc.getDocumentElement();
                    String response = handleIq(iqEl);
                    if (response != null) {
                        out.write(response);
                        out.flush();
                    }

                    // Return remaining data after this stanza
                    String remaining = data.substring(0, iqStart) + data.substring(endIndex);
                    return remaining.trim().isEmpty() ? "" : remaining;

                } catch (Exception e) {
                    // Parsing failed, return original data (silently)
                }
            }
        }

        // No complete stanzas found, return original data
        return data;
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
}