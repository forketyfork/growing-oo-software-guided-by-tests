package me.forketyfork.growing.xmpp;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Represents an active XMPP client session with thread-safe write operations.
 */
public class ClientSession {
    private static final Logger logger = Logger.getLogger("ClientSession");
    
    private final String username;
    private final String fullJid;
    private final String bareJid;
    private final XMLStreamWriter xmlWriter;
    private final ReentrantLock writeLock = new ReentrantLock();
    
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
     * Thread-safe method to write XML stanzas to the client's stream.
     * This is essential since XMLStreamWriter is not thread-safe and
     * messages may be routed from other client threads.
     */
    public synchronized void writeStanza(String xmlStanza) throws XMLStreamException {
        writeLock.lock();
        try {
            xmlWriter.writeCharacters(xmlStanza);
            xmlWriter.flush();
        } finally {
            writeLock.unlock();
        }
    }
    
    /**
     * Thread-safe method to write a complete message stanza.
     */
    public synchronized void writeMessageStanza(String from, String to, String body) throws XMLStreamException {
        writeLock.lock();
        try {
            logger.log(Level.INFO, "XML: Writing message stanza to client {0}: from={1}, to={2}, body={3}", new Object[]{fullJid, from, to, body});
            
            xmlWriter.writeStartElement("message");
            if (from != null) {
                xmlWriter.writeAttribute("from", from);
            }
            if (to != null) {
                xmlWriter.writeAttribute("to", to);
            }
            xmlWriter.writeAttribute("type", "chat");
            
            if (body != null && !body.isEmpty()) {
                xmlWriter.writeStartElement("body");
                xmlWriter.writeCharacters(body);
                xmlWriter.writeEndElement(); // body
            }
            
            xmlWriter.writeEndElement(); // message
            xmlWriter.flush();
            
            logger.log(Level.INFO, "XML: Message stanza written and flushed to client {0}", fullJid);
        } finally {
            writeLock.unlock();
        }
    }
}