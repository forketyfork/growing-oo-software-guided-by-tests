package me.forketyfork.growing;

import org.jivesoftware.smack.chat2.Chat;
import org.jivesoftware.smack.chat2.IncomingChatMessageListener;
import org.jivesoftware.smack.packet.Message;
import org.jxmpp.jid.EntityBareJid;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

public class SingleMessageListener implements IncomingChatMessageListener {

    private final ArrayBlockingQueue<Message> messages = new ArrayBlockingQueue<>(1);

    public void receivesAMessage() throws InterruptedException {
        assertThat("Message", messages.poll(5, TimeUnit.SECONDS), is(notNullValue()));
    }

    @Override
    public void newIncomingMessage(EntityBareJid from, Message message, Chat chat) {
        messages.add(message);
    }
}
