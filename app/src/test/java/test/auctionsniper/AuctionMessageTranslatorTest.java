package test.auctionsniper;

import org.jivesoftware.smack.chat2.Chat;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.MessageBuilder;
import org.junit.jupiter.api.Test;

public class AuctionMessageTranslatorTest {
    public static final Chat UNUSED_CHAT = null;

    private final AuctionMessageTranslator translator = new AuctionMessageTranslator();

    @Test
    public void notifiesAuctionClosedWhenCloseMessageReceived() {

        Message message = MessageBuilder.buildMessage().setBody("SOLVersion: 1.1; Event: CLOSE;").build();
        translator.processMessage(UNUSED_CHAT, message);
    }
}
