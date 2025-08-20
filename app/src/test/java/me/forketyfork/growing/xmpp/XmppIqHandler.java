package me.forketyfork.growing.xmpp;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

/**
 * Interface for handling IQ stanzas.
 */
public interface XmppIqHandler {
    
    /**
     * Handle IQ stanza.
     * @param xmlReader the XML stream reader
     * @param xmlWriter the XML stream writer
     * @param currentState the current client state
     * @return the new client state
     * @throws XMLStreamException if XML processing fails
     */
    ClientState handleIqStanza(XMLStreamReader xmlReader, XMLStreamWriter xmlWriter, 
                              ClientState currentState) throws XMLStreamException;
}