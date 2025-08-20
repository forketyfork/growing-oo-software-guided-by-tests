package me.forketyfork.growing.xmpp;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Default implementation of XmppSaslHandler.
 */
public class DefaultSaslHandler implements XmppSaslHandler {
    
    private final Logger logger = Logger.getLogger("DefaultSaslHandler");
    
    @Override
    public ClientState handleSaslAuth(XMLStreamReader xmlReader, XMLStreamWriter xmlWriter, 
                                     ClientState currentState) throws XMLStreamException {
        logger.log(Level.FINE, "Handling SASL Auth, currentState: {0}", currentState);
        
        // Read the auth content (we don't need to validate it for this test server)
        StringBuilder authContent = new StringBuilder();
        while (xmlReader.hasNext()) {
            int event = xmlReader.next();
            if (event == XMLStreamConstants.CHARACTERS) {
                authContent.append(xmlReader.getText());
            } else if (event == XMLStreamConstants.END_ELEMENT &&
                    "auth".equals(xmlReader.getName().getLocalPart())) {
                break;
            }
        }
        
        logger.log(Level.FINE, "Received SASL Auth: {0}, sending auth success", authContent);
        
        // Send SASL success
        xmlWriter.writeStartElement("success");
        xmlWriter.writeAttribute("xmlns", XmppServerConfig.NAMESPACE_SASL);
        xmlWriter.writeEndElement();
        xmlWriter.flush();
        return ClientState.AUTHENTICATED_WAITING_FOR_RESTART;
    }
}