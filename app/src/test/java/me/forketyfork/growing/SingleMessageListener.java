package me.forketyfork.growing;

import org.hamcrest.Matcher;
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
    private Chat currentChat;

    public void receivesAMessage(Matcher<? super String> messageMatcher) throws InterruptedException {
        final Message message = messages.poll(5, TimeUnit.SECONDS);
        assertThat("Message", message, is(notNullValue()));
        assertThat(message.getBody(), messageMatcher);
    }

    public Chat getCurrentChat() {
        return currentChat;
    }

    @Override
    public void newIncomingMessage(EntityBareJid from, Message message, Chat chat) {
        messages.add(message);
        this.currentChat = chat;
    }
}
