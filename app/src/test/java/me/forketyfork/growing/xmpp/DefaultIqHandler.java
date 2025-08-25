package me.forketyfork.growing.xmpp;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Default implementation of XmppIqHandler.
 */
public class DefaultIqHandler implements XmppIqHandler {
    
    private final Logger logger = Logger.getLogger("DefaultIqHandler");
    
    @Override
    public ClientContext handleIqStanza(XMLStreamReader xmlReader, ClientContext context) throws XMLStreamException {
        logger.log(Level.FINE, "Handling IQ Stanza, currentState: {0}", context.getState());
        
        // Read IQ attributes
        String type = xmlReader.getAttributeValue(null, "type");
        String id = xmlReader.getAttributeValue(null, "id");
        if (id == null || id.isEmpty()) id = "response";
        
        // Parse the IQ content
        String queryNs = null;
        boolean hasBind = false;
        String requestedResource = null;
        
        // Read the IQ content
        while (xmlReader.hasNext()) {
            int event = xmlReader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                QName elementName = xmlReader.getName();
                String localName = elementName.getLocalPart();
                String namespace = elementName.getNamespaceURI();
                
                if ("query".equals(localName)) {
                    queryNs = namespace;
                } else if ("bind".equals(localName) && XmppServerConfig.NAMESPACE_BIND.equals(namespace)) {
                    hasBind = true;
                } else if ("resource".equals(localName) && hasBind) {
                    // Read the requested resource
                    requestedResource = xmlReader.getElementText();
                }
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                if ("iq".equals(xmlReader.getName().getLocalPart())) {
                    break;
                }
            }
        }
        
        // Handle resource binding - assign JID and register client
        if (hasBind && context.getUsername() != null) {
            String resource = requestedResource;
            if (resource == null || resource.trim().isEmpty()) {
                // Generate a unique resource if none provided
                resource = "resource-" + UUID.randomUUID().toString().substring(0, 8);
            }
            
            String fullJid = context.getUsername() + "@localhost/" + resource;
            context.setFullJid(fullJid);
            
            // Register the client in the global registry
            context.registerClient();
            
            logger.log(Level.INFO, "ASSIGNED JID: {0} to client, registering in registry", fullJid);
        }
        
        // Generate response
        sendIqResponse(context.getXmlWriter(), type, id, queryNs, hasBind, context);
        return context;
    }
    
    private void sendIqResponse(XMLStreamWriter xmlWriter, String type, String id, String queryNs,
                               boolean hasBind, ClientContext context) throws XMLStreamException {
        xmlWriter.writeStartElement("iq");
        xmlWriter.writeAttribute("type", "result");
        xmlWriter.writeAttribute("id", id);
        
        if (queryNs != null) {
            if (XmppServerConfig.NAMESPACE_IQ_AUTH.equals(queryNs)) {
                if ("get".equals(type)) {
                    xmlWriter.writeStartElement("query");
                    xmlWriter.writeAttribute("xmlns", XmppServerConfig.NAMESPACE_IQ_AUTH);
                    xmlWriter.writeEmptyElement("username");
                    xmlWriter.writeEmptyElement("password");
                    xmlWriter.writeEmptyElement("resource");
                    xmlWriter.writeEndElement(); // query
                }
            } else if (XmppServerConfig.NAMESPACE_IQ_ROSTER.equals(queryNs) && "get".equals(type)) {
                xmlWriter.writeEmptyElement("query");
                xmlWriter.writeAttribute("xmlns", XmppServerConfig.NAMESPACE_IQ_ROSTER);
            }
        } else if (hasBind) {
            xmlWriter.writeStartElement("bind");
            xmlWriter.writeAttribute("xmlns", XmppServerConfig.NAMESPACE_BIND);
            xmlWriter.writeStartElement("jid");
            // Use the assigned JID instead of hardcoded value
            String jidToReturn = context.getFullJid() != null ? context.getFullJid() : "test@localhost/resource";
            xmlWriter.writeCharacters(jidToReturn);
            xmlWriter.writeEndElement(); // jid
            xmlWriter.writeEndElement(); // bind
        }
        
        xmlWriter.writeEndElement(); // iq
        xmlWriter.flush();
    }
}