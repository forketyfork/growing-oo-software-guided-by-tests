package me.forketyfork.growing;

import me.forketyfork.growing.auctionsniper.AuctionEventListener;
import me.forketyfork.growing.auctionsniper.AuctionMessageTranslator;
import me.forketyfork.growing.auctionsniper.ui.MainWindow;
import org.jivesoftware.smack.AbstractXMPPConnection;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.chat2.Chat;
import org.jivesoftware.smack.chat2.ChatManager;
import org.jivesoftware.smack.tcp.XMPPTCPConnection;
import org.jivesoftware.smack.tcp.XMPPTCPConnectionConfiguration;
import org.jxmpp.jid.impl.JidCreate;
import org.jxmpp.jid.parts.Resourcepart;
import org.jxmpp.stringprep.XmppStringprepException;

import javax.swing.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;

import static org.jivesoftware.smack.ConnectionConfiguration.SecurityMode;

public class Main implements AuctionEventListener {

    public static final int ARG_HOSTNAME = 0;
    public static final int ARG_USERNAME = 1;
    public static final int ARG_PASSWORD = 2;
    public static final int ARG_ITEM_ID = 3;

    public static final String AUCTION_RESOURCE = "Auction";
    public static final String ITEM_ID_AS_LOGIN = "auction-%s";
    public static final String AUCTION_ID_FORMAT = ITEM_ID_AS_LOGIN + "@%s/" + AUCTION_RESOURCE;

    // event and command formats
    public static final String REPORT_PRICE_EVENT_FORMAT = "SOLVersion: 1.1; Event: PRICE; CurrentPrice: %d; Increment: %d; Bidder: %s;";
    public static final String BID_COMMAND_FORMAT = "SOLVersion: 1.1; Command: BID; Price: %d;";
    public static final String JOIN_COMMAND_FORMAT = "SOLVersion: 1.1; Command: JOIN;";

    private MainWindow ui;

    @SuppressWarnings("unused")
    private Chat notToBeGCd;

    public Main() throws Exception {
        startUserInterface();
    }

    public static void main(String... args) throws Exception {
        Main main = new Main();
        main.joinAuction(connectTo(args[ARG_HOSTNAME], args[ARG_USERNAME], args[ARG_PASSWORD]), args[ARG_ITEM_ID]);
    }

    @Override
    public void auctionClosed() {
        SwingUtilities.invokeLater(() -> ui.showStatus(MainWindow.STATUS_LOST));
    }

    @Override
    public void currentPrice(int price, int increment) {
        // TODO
    }

    private void joinAuction(AbstractXMPPConnection connection, String itemId) throws SmackException.NotConnectedException, InterruptedException, XmppStringprepException {
        disconnectWhenCloses(connection);
        var chatManager = ChatManager.getInstanceFor(connection);
        chatManager.addIncomingListener(new AuctionMessageTranslator(this));

        var chat = chatManager.chatWith(JidCreate.from(auctionId(itemId, connection)).asEntityBareJidOrThrow());
        chat.send(JOIN_COMMAND_FORMAT);
    }

    private void disconnectWhenCloses(AbstractXMPPConnection connection) {
        ui.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                connection.disconnect();
            }
        });
    }

    private void startUserInterface() throws Exception {
        SwingUtilities.invokeAndWait((Runnable) () -> ui = MainWindow.createAndShow());
    }

    private static AbstractXMPPConnection connectTo(String hostname, String username, String password) throws XMPPException, IOException, SmackException, InterruptedException {
        var connection = new XMPPTCPConnection(XMPPTCPConnectionConfiguration.builder()
                .setHost(hostname)
                .setXmppDomain("localhost")
                .setPort(5222)
                .setSecurityMode(SecurityMode.disabled)
                .setCompressionEnabled(false)
                .build())
                .connect();

        connection.login(username, password, Resourcepart.from(AUCTION_RESOURCE));
        return connection;
    }

    private static String auctionId(String itemId, XMPPConnection connection) {
        return String.format(AUCTION_ID_FORMAT, itemId, connection.getXMPPServiceDomain());
    }

}
