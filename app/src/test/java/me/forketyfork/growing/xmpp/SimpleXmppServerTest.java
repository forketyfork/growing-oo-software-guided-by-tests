package me.forketyfork.growing.xmpp;

import org.jivesoftware.smack.AbstractXMPPConnection;
import org.jivesoftware.smack.chat2.Chat;
import org.jivesoftware.smack.chat2.ChatManager;
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
        XMPPTCPConnectionConfiguration config = XMPPTCPConnectionConfiguration.builder()
                .setHost("localhost")
                .setXmppDomain("localhost")
                .setPort(port)
                .setSecurityMode(org.jivesoftware.smack.ConnectionConfiguration.SecurityMode.disabled)
                .setCompressionEnabled(false)
                .build();
        AbstractXMPPConnection connection = new XMPPTCPConnection(config);
        connection.connect();
        connection.login(user, "password", Resourcepart.from(resource));
        connections.add(connection);
        return connection;
    }

    @Test
    public void serverAcceptsConnectionsAndStops() throws Exception {
        int port = freePort();
        server = new SimpleXmppServer(new XmppServerConfig(port, "localhost", 200, 1000, 100));
        server.start();

        try (Socket socket = new Socket("localhost", port)) {
            assertTrue(socket.isConnected());
        }

        server.stop();

        // After stop the port should be unavailable
        assertThrows(ConnectException.class, () -> new Socket("localhost", port));
    }

    @Test
    public void routesMessagesBetweenClients() throws Exception {
        int port = freePort();
        server = new SimpleXmppServer(new XmppServerConfig(port, "localhost", 200, 1000, 100));
        server.start();

        AbstractXMPPConnection alice = newConnection(port, "alice", "res1");
        AbstractXMPPConnection bob = newConnection(port, "bob", "res2");

        List<String> messages = new ArrayList<>();
        ChatManager.getInstanceFor(bob).addIncomingListener((from, message, chat) -> messages.add(message.getBody()));

        EntityBareJid bobJid = JidCreate.entityBareFrom("bob@localhost");
        Chat chat = ChatManager.getInstanceFor(alice).chatWith(bobJid);
        chat.send("hello");

        // Wait up to 2 seconds for message delivery
        for (int i = 0; i < 20 && messages.isEmpty(); i++) {
            Thread.sleep(100);
        }

        assertEquals(1, messages.size());
        assertEquals("hello", messages.get(0));
    }
}
