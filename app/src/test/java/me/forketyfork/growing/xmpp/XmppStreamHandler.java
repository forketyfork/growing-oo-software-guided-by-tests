package me.forketyfork.growing.xmpp;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

/**
 * Interface for handling XMPP stream events.
 */
public interface XmppStreamHandler {
    
    /**
     * Handle stream start element.
     * @param xmlReader the XML stream reader
     * @param context the client context containing state and connection info
     * @return the updated client context
     * @throws XMLStreamException if XML processing fails
     */
    ClientContext handleStreamStart(XMLStreamReader xmlReader, ClientContext context) throws XMLStreamException;
    
    /**
     * Handle stream end element.
     * @param xmlReader the XML stream reader
     * @param context the client context containing state and connection info
     * @return the updated client context
     * @throws XMLStreamException if XML processing fails
     */
    ClientContext handleStreamEnd(XMLStreamReader xmlReader, ClientContext context) throws XMLStreamException;
}