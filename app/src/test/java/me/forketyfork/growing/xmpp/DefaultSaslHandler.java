package me.forketyfork.growing.xmpp;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import java.util.Base64;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Default implementation of XmppSaslHandler.
 */
public class DefaultSaslHandler implements XmppSaslHandler {
    
    private final Logger logger = Logger.getLogger("DefaultSaslHandler");
    
    @Override
    public ClientContext handleSaslAuth(XMLStreamReader xmlReader, ClientContext context) throws XMLStreamException {
        logger.log(Level.FINE, "Handling SASL Auth, currentState: {0}", context.getState());
        
        // Read the auth content
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
        
        // Extract username from SASL PLAIN authentication
        String username = extractUsernameFromSaslPlain(authContent.toString());
        if (username != null) {
            context.setUsername(username);
            logger.log(Level.FINE, "Extracted username: {0}", username);
        }
        
        logger.log(Level.FINE, "Received SASL Auth: {0}, sending auth success", authContent);
        
        // Send SASL success
        XMLStreamWriter xmlWriter = context.getXmlWriter();
        xmlWriter.writeStartElement("success");
        xmlWriter.writeAttribute("xmlns", XmppServerConfig.NAMESPACE_SASL);
        xmlWriter.writeEndElement();
        xmlWriter.flush();
        
        context.setState(ClientState.AUTHENTICATED_WAITING_FOR_RESTART);
        return context;
    }
    
    /**
     * Extract username from SASL PLAIN mechanism.
     * SASL PLAIN format: base64([authzid] \0 authcid \0 password)
     */
    private String extractUsernameFromSaslPlain(String base64Auth) {
        try {
            if (base64Auth == null || base64Auth.trim().isEmpty()) {
                return null;
            }
            
            byte[] decoded = Base64.getDecoder().decode(base64Auth.trim());
            String plainText = new String(decoded);
            
            // Split by null character (\0)
            String[] parts = plainText.split("\0");
            if (parts.length >= 2) {
                // parts[0] is authzid (optional), parts[1] is username
                return parts.length == 2 ? parts[0] : parts[1];
            }
            
            return null;
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to parse SASL PLAIN auth", e);
            return null;
        }
    }
}