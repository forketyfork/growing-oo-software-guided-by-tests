package me.forketyfork.growing;

import me.forketyfork.growing.xmpp.SimpleXmppServer;
import me.forketyfork.growing.xmpp.XmppServerConfig;
import org.hamcrest.Matcher;
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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

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
        ensureServerStarted(itemId);
        connection.connect();
        connection.login(String.format(ITEM_ID_AS_LOGIN, itemId), AUCTION_PASSWORD, Resourcepart.from(AUCTION_RESOURCE));
        var chatManager = ChatManager.getInstanceFor(connection);
        chatManager.addIncomingListener(messageListener);
        // Don't create a chat - wait for the sniper to initiate communication
    }

    private static synchronized void ensureServerStarted(String itemId) {
        if (embeddedServer == null) {
            XmppServerConfig config = XmppServerConfig.builder()
                    .port(5222)
                    .serverName(XMPP_HOSTNAME)
                    .socketTimeoutMs(1000)
                    .shutdownTimeoutMs(5000)
                    .maxConnections(100)
                    .addUser(String.format(ITEM_ID_AS_LOGIN, itemId), AUCTION_PASSWORD)
                    .addUser(ApplicationRunner.SNIPER_ID, ApplicationRunner.SNIPER_PASSWORD)
                    .build();
            embeddedServer = new SimpleXmppServer(config);
            try {
                embeddedServer.start();
            } catch (IOException e) {
                throw new RuntimeException("Failed to start embedded XMPP server", e);
            }
        }
    }

    public void hasReceivedJoinRequestFrom(String sniperId) throws InterruptedException {
        receivesAMessageMatching(sniperId, equalTo(Main.JOIN_COMMAND_FORMAT));
    }

    public void hasReceivedBid(int bid, String sniperId) throws InterruptedException {
        receivesAMessageMatching(sniperId, equalTo(String.format(Main.BID_COMMAND_FORMAT, bid)));
    }

    private void receivesAMessageMatching(String sniperId, Matcher<? super String> messageMatcher) throws InterruptedException {
        messageListener.receivesAMessage(messageMatcher);
        Chat currentChat = messageListener.getCurrentChat();
        assertThat(currentChat.getXmppAddressOfChatPartner().asUnescapedString(), equalTo(sniperId));
    }

    public void announceClosed() throws SmackException.NotConnectedException, InterruptedException {
        Chat chat = messageListener.getCurrentChat();
        if (chat != null) {
            chat.send("");
        }
    }

    public void stop() {
        connection.disconnect();
    }

    public static void stopEmbeddedServer() {
        embeddedServer.stop();
        embeddedServer = null;
    }

    public String getItemId() {
        return itemId;
    }

    public void reportPrice(int price, int increment, String bidder) throws SmackException.NotConnectedException, InterruptedException {
        Chat currentChat = messageListener.getCurrentChat();
        currentChat.send(String.format(Main.REPORT_PRICE_EVENT_FORMAT, price, increment, bidder));
    }

}
