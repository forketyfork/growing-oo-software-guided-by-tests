package me.forketyfork.growing;

import org.jivesoftware.smack.Chat;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.packet.Message;

public class FakeAuctionServer {

    public static final String ITEM_ID_AS_LOGIN = "auction-%s";
    public static final String AUCTION_RESOURCE = "Auction";
    public static final String XMPP_HOSTNAME = "localhost";
    public static final String AUCTION_PASSWORD = "auction";

    private final String itemId;
    private final XMPPConnection connection;
    private Chat currentChat;

    private final SingleMessageListener messageListener = new SingleMessageListener();

    public FakeAuctionServer(String itemId) {
        this.itemId = itemId;
        this.connection = new XMPPConnection(XMPP_HOSTNAME);
    }

    public void startSellingItem() throws XMPPException {
        connection.connect();
        connection.login(String.format(ITEM_ID_AS_LOGIN, itemId), AUCTION_PASSWORD, AUCTION_RESOURCE);
        connection.getChatManager().addChatListener(
                (chat, createdLocally) -> {
                    currentChat = chat;
                    chat.addMessageListener(messageListener);

                }
        );
    }

    public void hasReceivedJoinRequestFromSniper() throws InterruptedException {
        messageListener.receivesAMessage();
    }

    public void announceClosed() throws XMPPException {
        currentChat.sendMessage(new Message());
    }

    public void stop() {
        connection.disconnect();
    }

    public String getItemId() {
        return itemId;
    }

}

