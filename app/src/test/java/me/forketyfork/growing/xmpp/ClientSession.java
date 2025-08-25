package me.forketyfork.growing.xmpp;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Represents an active XMPP client session with message queue-based delivery.
 * Each client processes its own message queue in its own thread to avoid
 * cross-thread XMLStreamWriter issues.
 */
public class ClientSession {
    private static final Logger logger = Logger.getLogger("ClientSession");

    private final String username;
    private final String fullJid;
    private final String bareJid;
    private final XMLStreamWriter xmlWriter;
    private final BlockingQueue<PendingMessage> messageQueue = new LinkedBlockingQueue<>();

    public ClientSession(String username, String fullJid, String bareJid, XMLStreamWriter xmlWriter) {
        this.username = username;
        this.fullJid = fullJid;
        this.bareJid = bareJid;
        this.xmlWriter = xmlWriter;
    }

    public String getUsername() {
        return username;
    }

    public String getFullJid() {
        return fullJid;
    }

    public String getBareJid() {
        return bareJid;
    }

    /**
     * Queue a message for delivery to this client.
     * This method is thread-safe and can be called from any thread.
     */
    public void queueMessage(String from, String to, String body) {
        PendingMessage message = new PendingMessage(from, to, body);
        messageQueue.offer(message);
        logger.log(Level.INFO, "QUEUE: Message queued for client {0}: from={1}, to={2}, body={3}",
                new Object[]{fullJid, from, to, body});
    }

    /**
     * Process all pending messages in the queue and write them to the XML stream.
     * This method must ONLY be called from the client's own thread to maintain
     * XMLStreamWriter thread safety.
     */
    public synchronized void processPendingMessages() throws XMLStreamException {
        int messageCount = messageQueue.size();
        if (messageCount > 0) {
            logger.log(Level.INFO, "PROCESSING: Starting to process {0} pending messages for client {1}", new Object[]{messageCount, fullJid});
        }
        PendingMessage message;
        while ((message = messageQueue.poll()) != null) {
            logger.log(Level.INFO, "XML: Writing message stanza to client {0} from thread {1}: from={2}, to={3}, body={4}",
                    new Object[]{fullJid, Thread.currentThread().getName(), message.from, message.to, message.body});

            xmlWriter.writeStartElement("jabber:client", "message");
            if (message.from != null) {
                xmlWriter.writeAttribute("from", message.from);
            }
            if (message.to != null) {
                xmlWriter.writeAttribute("to", message.to);
            }
            xmlWriter.writeAttribute("type", "chat");

            if (message.body != null && !message.body.isEmpty()) {
                xmlWriter.writeStartElement("jabber:client", "body");
                xmlWriter.writeCharacters(message.body);
                xmlWriter.writeEndElement(); // body
            }

            xmlWriter.writeEndElement(); // message
            xmlWriter.flush();

            logger.log(Level.INFO, "XML: Message stanza written and flushed to client {0}", fullJid);
        }
    }

    /**
     * Check if there are pending messages to be processed.
     */
    public boolean hasPendingMessages() {
        return !messageQueue.isEmpty();
    }

    /**
     * Get the XMLStreamWriter for this client.
     * Should only be used by the client's own thread.
     */
    public XMLStreamWriter getXmlWriter() {
        return xmlWriter;
    }

    /**
     * Internal class to represent a pending message.
     */
    private static class PendingMessage {
        final String from;
        final String to;
        final String body;

        PendingMessage(String from, String to, String body) {
            this.from = from;
            this.to = to;
            this.body = body;
        }
    }
}