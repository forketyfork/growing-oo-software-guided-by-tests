package me.forketyfork.growing.test.auctionsniper;

import me.forketyfork.growing.auctionsniper.AuctionEventListener;
import me.forketyfork.growing.auctionsniper.AuctionMessageTranslator;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.MessageBuilder;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.junit.jupiter.api.Test;


public class AuctionMessageTranslatorTest {

    private final Mockery context = new Mockery();
    private final AuctionEventListener listener = context.mock(AuctionEventListener.class);
    private final AuctionMessageTranslator translator = new AuctionMessageTranslator();

    @Test
    public void notifiesAuctionClosedWhenCloseMessageReceived() {

        context.checking(new Expectations() {{
            oneOf(listener).auctionClosed();
        }});

        Message message = MessageBuilder.buildMessage().setBody("SOLVersion: 1.1; Event: CLOSE;").build();

        translator.processMessage(message);

        context.assertIsSatisfied();
    }
}
