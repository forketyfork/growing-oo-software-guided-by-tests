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
     * @param xmlWriter the XML stream writer
     * @param currentState the current client state
     * @return the new client state
     * @throws XMLStreamException if XML processing fails
     */
    ClientState handleSaslAuth(XMLStreamReader xmlReader, XMLStreamWriter xmlWriter, 
                              ClientState currentState) throws XMLStreamException;
}