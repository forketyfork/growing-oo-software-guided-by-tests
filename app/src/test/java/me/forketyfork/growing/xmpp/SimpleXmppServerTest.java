package me.forketyfork.growing.xmpp;

import org.jivesoftware.smack.AbstractXMPPConnection;
import org.jivesoftware.smack.chat2.Chat;
import org.jivesoftware.smack.chat2.ChatManager;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.tcp.XMPPTCPConnection;
import org.jivesoftware.smack.tcp.XMPPTCPConnectionConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.jxmpp.jid.EntityBareJid;
import org.jxmpp.jid.impl.JidCreate;
import org.jxmpp.jid.parts.Resourcepart;

import java.io.IOException;
import java.net.ConnectException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link SimpleXmppServer}.
 */
public class SimpleXmppServerTest {

    private SimpleXmppServer server;
    private final List<AbstractXMPPConnection> connections = new ArrayList<>();

    @AfterEach
    public void tearDown() {
        for (AbstractXMPPConnection c : connections) {
            if (c != null && c.isConnected()) {
                c.disconnect();
            }
        }
        if (server != null) {
            server.stop();
        }
        connections.clear();
    }

    private int freePort() throws IOException {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }

    private AbstractXMPPConnection newConnection(int port, String user, String resource) throws Exception {
        return newConnection(port, user, resource, "password");
    }

    private AbstractXMPPConnection newConnection(int port, String user, String resource, String password) throws Exception {
        XMPPTCPConnectionConfiguration config = XMPPTCPConnectionConfiguration.builder()
                .setHost("localhost")
                .setXmppDomain("localhost")
                .setPort(port)
                .setSecurityMode(org.jivesoftware.smack.ConnectionConfiguration.SecurityMode.disabled)
                .setCompressionEnabled(false)
                .build();
        AbstractXMPPConnection connection = new XMPPTCPConnection(config);
        try {
            connection.connect();
            connection.login(user, password, Resourcepart.from(resource));
            connections.add(connection);
            return connection;
        } catch (Exception e) {
            if (connection.isConnected()) {
                connection.disconnect();
            }
            throw e;
        }
    }

    @Test
    public void serverAcceptsConnectionsAndStops() throws Exception {
        int port = freePort();
        server = new SimpleXmppServer(new XmppServerConfig(port, "localhost", 200, 1000, 100, java.util.Map.of()));
        server.start();

        try (Socket socket = new Socket("localhost", port)) {
            assertTrue(socket.isConnected());
        }

        server.stop();

        // After stop, the port should be unavailable
        assertThrows(ConnectException.class, () -> new Socket("localhost", port));
    }

    @Test
    public void routesMessagesBetweenClients() throws Exception {
        int port = freePort();
        server = new SimpleXmppServer(new XmppServerConfig(port, "localhost", 200, 1000, 100, java.util.Map.of()));
        server.start();

        AbstractXMPPConnection alice = newConnection(port, "alice", "res1");
        AbstractXMPPConnection bob = newConnection(port, "bob", "res2");

        List<String> messages = new ArrayList<>();
        ChatManager.getInstanceFor(bob).addIncomingListener((_, message, _) -> messages.add(message.getBody()));

        EntityBareJid bobJid = JidCreate.entityBareFrom("bob@localhost");
        Chat chat = ChatManager.getInstanceFor(alice).chatWith(bobJid);
        chat.send("hello");

        // Wait up to 2 seconds for message delivery
        for (int i = 0; i < 20 && messages.isEmpty(); i++) {
            Thread.sleep(100);
        }

        assertEquals(1, messages.size());
        assertEquals("hello", messages.getFirst());
    }

    @Test
    public void rejectsConnectionsBeyondLimit() throws Exception {
        int port = freePort();
        server = new SimpleXmppServer(new XmppServerConfig(port, "localhost", 200, 1000, 1, java.util.Map.of()));
        server.start();

        AbstractXMPPConnection alice = newConnection(port, "alice", "res1");
        assertNotNull(alice); // first, connection succeeds

        assertThrows(Exception.class, () -> newConnection(port, "bob", "res2"));
    }

    @Test
    public void rejectsInvalidCredentials() throws Exception {
        int port = freePort();
        XmppServerConfig config = XmppServerConfig.builder()
                .port(port)
                .serverName("localhost")
                .socketTimeoutMs(200)
                .shutdownTimeoutMs(1000)
                .maxConnections(100)
                .addUser("alice", "secret")
                .build();
        server = new SimpleXmppServer(config);
        server.start();

        assertThrows(Exception.class, () -> newConnection(port, "alice", "res1", "wrong"));
    }

    @Test
    public void returnsErrorWhenRecipientUnavailable() throws Exception {
        int port = freePort();
        server = new SimpleXmppServer(new XmppServerConfig(port, "localhost", 200, 1000, 100, java.util.Map.of()));
        server.start();

        AbstractXMPPConnection alice = newConnection(port, "alice", "res1");

        List<Message> messages = new ArrayList<>();
        alice.addAsyncStanzaListener(stanza -> {
            if (stanza instanceof Message msg) {
                messages.add(msg);
            }
        }, stanza -> stanza instanceof Message);

        EntityBareJid bobJid = JidCreate.entityBareFrom("bob@localhost");
        Chat chat = ChatManager.getInstanceFor(alice).chatWith(bobJid);
        chat.send("hello");

        for (int i = 0; i < 20 && messages.isEmpty(); i++) {
            Thread.sleep(100);
        }

        assertEquals(1, messages.size());
        assertEquals("Message delivery failed: recipient-unavailable", messages.getFirst().getBody());
    }

    @Test
    public void rejectsDuplicateConnectionsForSameUser() throws Exception {
        int port = freePort();
        server = new SimpleXmppServer(new XmppServerConfig(port, "localhost", 200, 1000, 100, java.util.Map.of()));
        server.start();

        // First, connection should succeed
        AbstractXMPPConnection alice1 = newConnection(port, "alice", "res1");
        assertTrue(alice1.isAuthenticated());

        // Second connection with same username should be rejected with conflict
        assertThrows(Exception.class, () -> newConnection(port, "alice", "res2"));
    }
}
