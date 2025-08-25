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
     * @param context the client context containing state and connection info
     * @return the updated client context
     * @throws XMLStreamException if XML processing fails
     */
    ClientContext handleIqStanza(XMLStreamReader xmlReader, ClientContext context) throws XMLStreamException;
}