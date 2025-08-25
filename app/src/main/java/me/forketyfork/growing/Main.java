package me.forketyfork.growing;

import me.forketyfork.growing.auctionsniper.ui.MainWindow;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.chat2.Chat;
import org.jivesoftware.smack.chat2.ChatManager;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.tcp.XMPPTCPConnection;
import org.jivesoftware.smack.tcp.XMPPTCPConnectionConfiguration;
import org.jxmpp.jid.EntityBareJid;
import org.jxmpp.jid.impl.JidCreate;
import org.jxmpp.jid.parts.Resourcepart;

import javax.swing.*;
import java.io.IOException;

public class Main {

    public static final int ARG_HOSTNAME = 0;
    public static final int ARG_USERNAME = 1;
    public static final int ARG_PASSWORD = 2;
    public static final int ARG_ITEM_ID = 3;

    public static final String AUCTION_RESOURCE = "Auction";
    public static final String ITEM_ID_AS_LOGIN = "auction-%s";
    public static final String AUCTION_ID_FORMAT = ITEM_ID_AS_LOGIN + "@%s/" + AUCTION_RESOURCE;

    private MainWindow ui;

    public Main() throws Exception {
        startUserInterface();
    }

    public static void main(String... args) throws Exception {
        Main main = new Main();

        XMPPConnection connection = connectTo(args[ARG_HOSTNAME], args[ARG_USERNAME], args[ARG_PASSWORD]);

        var chatManager = ChatManager.getInstanceFor(connection);
        chatManager.addIncomingListener((EntityBareJid from, Message message, Chat chat) -> {
            // nothing yet
        });

        var chat = chatManager.chatWith(JidCreate.from(auctionId(args[ARG_ITEM_ID], connection)).asEntityBareJidOrThrow());
        chat.send("");
    }

    private void startUserInterface() throws Exception {
        SwingUtilities.invokeAndWait((Runnable) () -> ui = new MainWindow());
    }

    private static XMPPConnection connectTo(String hostname, String username, String password) throws XMPPException, IOException, SmackException, InterruptedException {
        var connection = new XMPPTCPConnection(XMPPTCPConnectionConfiguration.builder()
                .setHost(hostname)
                .setXmppDomain("localhost")
                .setPort(5222)
                .setSecurityMode(org.jivesoftware.smack.ConnectionConfiguration.SecurityMode.disabled)
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
