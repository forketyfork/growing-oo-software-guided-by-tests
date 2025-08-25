package me.forketyfork.growing;

import me.forketyfork.growing.xmpp.SimpleXmppServer;
import me.forketyfork.growing.xmpp.XmppServerConfig;
import org.jivesoftware.smack.AbstractXMPPConnection;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.chat2.Chat;
import org.jivesoftware.smack.chat2.ChatManager;
import org.jivesoftware.smack.tcp.XMPPTCPConnection;
import org.jivesoftware.smack.tcp.XMPPTCPConnectionConfiguration;
import org.jxmpp.jid.parts.Resourcepart;
import org.jxmpp.stringprep.XmppStringprepException;

import java.io.IOException;

public class FakeAuctionServer {

    public static final String ITEM_ID_AS_LOGIN = "auction-%s";
    public static final String AUCTION_RESOURCE = "Auction";
    public static final String XMPP_HOSTNAME = "localhost";
    public static final String AUCTION_PASSWORD = "auction";

    private static SimpleXmppServer embeddedServer;

    private final String itemId;
    private final AbstractXMPPConnection connection;


    private final SingleMessageListener messageListener = new SingleMessageListener();

    public FakeAuctionServer(String itemId) throws XmppStringprepException {
        this.itemId = itemId;
        this.connection = new XMPPTCPConnection(XMPPTCPConnectionConfiguration.builder()
                .setHost(XMPP_HOSTNAME)
                .setXmppDomain(XMPP_HOSTNAME)
                .setPort(5222)
                .setSecurityMode(org.jivesoftware.smack.ConnectionConfiguration.SecurityMode.disabled)
                .setCompressionEnabled(false)
                .build());
    }

    public void startSellingItem() throws XMPPException, SmackException, IOException, InterruptedException {
        ensureServerStarted();
        connection.connect();
        connection.login(String.format(ITEM_ID_AS_LOGIN, itemId), AUCTION_PASSWORD, Resourcepart.from(AUCTION_RESOURCE));
        var chatManager = ChatManager.getInstanceFor(connection);
        chatManager.addIncomingListener(messageListener);
        // Don't create a chat - wait for the sniper to initiate communication
    }

    private static synchronized void ensureServerStarted() {
        if (embeddedServer == null) {
            // Use shorter timeout for testing to allow faster message processing
            embeddedServer = new SimpleXmppServer(new XmppServerConfig(5222, "localhost", 1000, 5000, 100));
            try {
                embeddedServer.start();
            } catch (IOException e) {
                throw new RuntimeException("Failed to start embedded XMPP server", e);
            }
        }
    }

    public void hasReceivedJoinRequestFromSniper() throws InterruptedException {
        messageListener.receivesAMessage();
    }

    public void announceClosed() throws SmackException.NotConnectedException, InterruptedException {
        Chat chat = messageListener.getCurrentChat();
        if (chat != null) {
            chat.send("");
        }
    }

    public void stop() {
        connection.disconnect();
        embeddedServer.stop();
    }

    public String getItemId() {
        return itemId;
    }

}

