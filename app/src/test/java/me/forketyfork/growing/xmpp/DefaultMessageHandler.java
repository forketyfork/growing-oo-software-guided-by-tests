package me.forketyfork.growing.xmpp;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DefaultMessageHandler implements XmppMessageHandler {

    private static final Logger logger = Logger.getLogger("DefaultMessageHandler");
    private static final BlockingQueue<MessageInfo> messageQueue = new LinkedBlockingQueue<>();

    @Override
    public ClientContext handleMessageStanza(XMLStreamReader xmlReader, ClientContext context) throws XMLStreamException {
        logger.log(Level.INFO, "Handling message stanza, currentState: {0}", context.getState());

        String from = xmlReader.getAttributeValue(null, "from");
        String to = xmlReader.getAttributeValue(null, "to");
        String type = xmlReader.getAttributeValue(null, "type");

        logger.log(Level.INFO, "Message - from: {0}, to: {1}, type: {2}", new Object[]{from, to, type});
        logger.log(Level.INFO, "ROUTING ATTEMPT: Message routing will be attempted for to='{0}'", to);

        // Read the message content
        StringBuilder messageBody = new StringBuilder();
        int depth = 1;

        while (xmlReader.hasNext() && depth > 0) {
            int event = xmlReader.next();
            switch (event) {
                case XMLStreamReader.START_ELEMENT:
                    depth++;
                    if ("body".equals(xmlReader.getLocalName())) {
                        messageBody.append(xmlReader.getElementText());
                        depth--; // getElementText() positions reader at END_ELEMENT
                    }
                    break;
                case XMLStreamReader.END_ELEMENT:
                    depth--;
                    break;
                case XMLStreamReader.CHARACTERS:
                    if (depth == 1) {
                        messageBody.append(xmlReader.getText());
                    }
                    break;
            }
        }

        // IMPORTANT: Implement message routing instead of just queuing
        if (to != null && !to.trim().isEmpty()) {
            routeMessage(context, from, to, messageBody.toString());
        } else {
            logger.log(Level.WARNING, "Message has no 'to' attribute, cannot route");
        }

        // Still add to queue for backwards compatibility (in case any tests rely on it)
        MessageInfo messageInfo = new MessageInfo(from, to, messageBody.toString());
        messageQueue.offer(messageInfo);
        logger.log(Level.INFO, "Message queued: {0}", messageInfo);

        return context;
    }

    /**
     * Route a message from sender to target client.
     * This is the core functionality that enables client-to-client communication.
     */
    private void routeMessage(ClientContext senderContext, String originalFrom, String to, String body) {
        try {
            // Use sender's full JID as the 'from' address (overrides client-provided 'from')
            String actualFrom = senderContext.getFullJid();
            if (actualFrom == null) {
                logger.log(Level.WARNING, "Sender has no assigned JID, cannot route message");
                return;
            }

            logger.log(Level.INFO, "ROUTING: Attempting to route message from {0} to {1}", new Object[]{actualFrom, to});
            logger.log(Level.INFO, "ROUTING: Available clients in registry: {0}", senderContext.getClientRegistry().keySet());

            // Find target client session
            ClientSession targetSession = senderContext.findClientSession(to);
            logger.log(Level.INFO, "ROUTING: Direct lookup for ''{0}'' found: {1}", new Object[]{to, targetSession != null});

            // If not found by exact JID, try bare JID (remove resource part)
            if (targetSession == null) {
                int resourceIndex = to.indexOf('/');
                if (resourceIndex >= 0) {
                    String bareJid = to.substring(0, resourceIndex);
                    targetSession = senderContext.findClientSession(bareJid);
                    logger.log(Level.INFO, "ROUTING: Bare JID lookup for ''{0}'' found: {1}", new Object[]{bareJid, targetSession != null});
                } else {
                    // 'to' is already a bare JID, but let's also try exact match with full JIDs
                    logger.log(Level.INFO, "ROUTING: Target ''{0}'' is already bare JID, checking full JIDs", to);
                    for (String registeredJid : senderContext.getClientRegistry().keySet()) {
                        if (registeredJid.startsWith(to + "/")) {
                            targetSession = senderContext.findClientSession(registeredJid);
                            logger.log(Level.INFO, "ROUTING: Found matching full JID: {0}", registeredJid);
                            break;
                        }
                    }
                }
            }

            if (targetSession != null) {
                // Queue the message for the target client - it will be processed by the client's own thread
                String targetJid = targetSession.getFullJid();
                targetSession.queueMessage(actualFrom, targetJid, body);
                logger.log(Level.INFO, "SUCCESS: Message queued for routing from {0} to {1} (target full JID: {2})", new Object[]{actualFrom, to, targetJid});
                logger.log(Level.INFO, "QUEUE STATUS: Message added to queue, target session has {0} pending messages", targetSession.hasPendingMessages() ? "some" : "no");

                // Note: Cannot immediately process messages here due to cross-thread XMLStreamWriter issues
                // The target client's own thread must process the messages to maintain XML stream integrity
            } else {
                logger.log(Level.WARNING, "FAILED: Target client not found: {0}. Available clients: {1}",
                        new Object[]{to, senderContext.getClientRegistry().keySet()});
                sendErrorResponse(senderContext, actualFrom, to, "recipient-unavailable");
            }

        } catch (Exception e) {
            logger.log(Level.SEVERE, "EXCEPTION: Failed to route message from " + originalFrom + " to " + to, e);
        }
    }

    /**
     * Send an error response back to the sender when message delivery fails.
     */
    private void sendErrorResponse(ClientContext senderContext, String from, String to, String errorType) {
        try {
            ClientSession senderSession = senderContext.findClientSession(senderContext.getFullJid());
            if (senderSession != null) {
                String errorBody = "Message delivery failed: " + errorType;
                senderSession.queueMessage("server", from, errorBody);
                logger.log(Level.INFO, "ERROR: Sent error response to {0} for failed delivery to {1}", new Object[]{from, to});
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to send error response to sender", e);
        }
    }

    public static MessageInfo pollMessage() {
        return messageQueue.poll();
    }

    public static boolean hasMessages() {
        return !messageQueue.isEmpty();
    }

    public static void clearMessages() {
        messageQueue.clear();
    }

    public static class MessageInfo {
        public final String from;
        public final String to;
        public final String body;

        public MessageInfo(String from, String to, String body) {
            this.from = from;
            this.to = to;
            this.body = body;
        }

        @Override
        public String toString() {
            return String.format("MessageInfo{from='%s', to='%s', body='%s'}", from, to, body);
        }
    }
}