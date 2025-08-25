package me.forketyfork.growing.xmpp;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

/**
 * Interface for handling SASL authentication events.
 */
public interface XmppSaslHandler {
    
    /**
     * Handle SASL authentication request.
     * @param xmlReader the XML stream reader
     * @param context the client context containing state and connection info
     * @return the updated client context
     * @throws XMLStreamException if XML processing fails
     */
    ClientContext handleSaslAuth(XMLStreamReader xmlReader, ClientContext context) throws XMLStreamException;
}