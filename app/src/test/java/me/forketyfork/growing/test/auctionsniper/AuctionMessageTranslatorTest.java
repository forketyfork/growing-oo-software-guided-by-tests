package me.forketyfork.growing.test.auctionsniper;

import me.forketyfork.growing.auctionsniper.AuctionEventListener;
import me.forketyfork.growing.auctionsniper.AuctionMessageTranslator;
import org.jivesoftware.smack.chat2.Chat;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.MessageBuilder;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.junit.jupiter.api.Test;
import org.jxmpp.jid.EntityBareJid;


public class AuctionMessageTranslatorTest {

    public static final Chat UNUSED_CHAT = null;
    public static final EntityBareJid UNUSED_ENTITY_BARE_JID = null;

    private final Mockery context = new Mockery();
    private final AuctionEventListener listener = context.mock(AuctionEventListener.class);
    private final AuctionMessageTranslator translator = new AuctionMessageTranslator(listener);

    @Test
    public void notifiesAuctionClosedWhenCloseMessageReceived() {

        context.checking(new Expectations() {{
            oneOf(listener).auctionClosed();
        }});

        Message message = MessageBuilder.buildMessage().setBody("SOLVersion: 1.1; Event: CLOSE;").build();

        translator.newIncomingMessage(UNUSED_ENTITY_BARE_JID, message, UNUSED_CHAT);

        context.assertIsSatisfied();
    }
}
