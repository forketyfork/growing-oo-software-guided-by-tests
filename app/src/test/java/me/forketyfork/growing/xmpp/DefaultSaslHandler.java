package me.forketyfork.growing.xmpp;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import java.util.Base64;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Default implementation of XmppSaslHandler.
 */
public class DefaultSaslHandler implements XmppSaslHandler {
    
    private final Logger logger = Logger.getLogger("DefaultSaslHandler");
    private final Map<String, String> userCredentials;

    public DefaultSaslHandler(Map<String, String> userCredentials) {
        this.userCredentials = userCredentials == null ? Map.of() : userCredentials;
    }
    
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
        
        // Extract username and password from SASL PLAIN authentication
        Credentials creds = extractCredentialsFromSaslPlain(authContent.toString());
        if (creds != null) {
            context.setUsername(creds.username);
            logger.log(Level.FINE, "Extracted username: {0}", creds.username);
        }

        boolean authorized = creds != null;
        if (authorized && !userCredentials.isEmpty()) {
            String expectedPassword = userCredentials.get(creds.username);
            authorized = expectedPassword != null && expectedPassword.equals(creds.password);
        }

        XMLStreamWriter xmlWriter = context.getXmlWriter();
        if (authorized) {
            logger.log(Level.FINE, "Received SASL Auth: {0}, sending auth success", authContent);
            xmlWriter.writeStartElement("success");
            xmlWriter.writeAttribute("xmlns", XmppServerConfig.NAMESPACE_SASL);
            xmlWriter.writeEndElement();
            xmlWriter.flush();
            context.setState(ClientState.AUTHENTICATED_WAITING_FOR_RESTART);
        } else {
            logger.log(Level.FINE, "Authentication failed for user {0}", creds == null ? "unknown" : creds.username);
            xmlWriter.writeStartElement("failure");
            xmlWriter.writeAttribute("xmlns", XmppServerConfig.NAMESPACE_SASL);
            xmlWriter.writeEmptyElement("not-authorized");
            xmlWriter.writeEndElement();
            xmlWriter.flush();
            context.setState(ClientState.CLOSED);
        }
        return context;
    }

    private Credentials extractCredentialsFromSaslPlain(String base64Auth) {
        try {
            if (base64Auth == null || base64Auth.trim().isEmpty()) {
                return null;
            }

            byte[] decoded = Base64.getDecoder().decode(base64Auth.trim());
            String plainText = new String(decoded);

            String[] parts = plainText.split("\0");
            if (parts.length >= 3) {
                // parts[0] is authzid (optional), parts[1] is username, parts[2] is password
                return new Credentials(parts[parts.length - 2], parts[parts.length - 1]);
            } else if (parts.length == 2) {
                // username\0password
                return new Credentials(parts[0], parts[1]);
            }
            return null;
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to parse SASL PLAIN auth", e);
            return null;
        }
    }

    private static class Credentials {
        final String username;
        final String password;

        Credentials(String username, String password) {
            this.username = username;
            this.password = password;
        }
    }
}