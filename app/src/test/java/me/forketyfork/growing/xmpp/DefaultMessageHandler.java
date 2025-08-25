package me.forketyfork.growing.xmpp;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DefaultMessageHandler implements XmppMessageHandler {

    private static final Logger logger = Logger.getLogger("DefaultMessageHandler");
    private static final BlockingQueue<MessageInfo> messageQueue = new LinkedBlockingQueue<>();

    @Override
    public ClientState handleMessageStanza(XMLStreamReader xmlReader, XMLStreamWriter xmlWriter, 
                                          ClientState currentState) throws XMLStreamException {
        logger.log(Level.INFO, "Handling message stanza, currentState: {0}", currentState);
        
        String from = xmlReader.getAttributeValue(null, "from");
        String to = xmlReader.getAttributeValue(null, "to");
        String type = xmlReader.getAttributeValue(null, "type");
        
        logger.log(Level.INFO, "Message - from: {0}, to: {1}, type: {2}", new Object[]{from, to, type});
        
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
        
        MessageInfo messageInfo = new MessageInfo(from, to, messageBody.toString());
        messageQueue.offer(messageInfo);
        logger.log(Level.INFO, "Message queued: {0}", messageInfo);
        
        return currentState;
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