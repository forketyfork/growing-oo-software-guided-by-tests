package me.forketyfork.growing.xmpp;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Default implementation of XmppIqHandler.
 */
public class DefaultIqHandler implements XmppIqHandler {
    
    private final Logger logger = Logger.getLogger("DefaultIqHandler");
    
    @Override
    public ClientState handleIqStanza(XMLStreamReader xmlReader, XMLStreamWriter xmlWriter, 
                                     ClientState currentState) throws XMLStreamException {
        logger.log(Level.FINE, "Handling IQ Stanza, currentState: {0}", currentState);
        
        // Read IQ attributes
        String type = xmlReader.getAttributeValue(null, "type");
        String id = xmlReader.getAttributeValue(null, "id");
        if (id == null || id.isEmpty()) id = "response";
        
        // Parse the IQ content
        String queryNs = null;
        boolean hasBind = false;
        
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
                }
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                if ("iq".equals(xmlReader.getName().getLocalPart())) {
                    break;
                }
            }
        }
        
        // Generate response
        sendIqResponse(xmlWriter, type, id, queryNs, hasBind);
        return currentState;
    }
    
    private void sendIqResponse(XMLStreamWriter xmlWriter, String type, String id, String queryNs,
                               boolean hasBind) throws XMLStreamException {
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
            xmlWriter.writeCharacters("test@localhost/resource");
            xmlWriter.writeEndElement(); // jid
            xmlWriter.writeEndElement(); // bind
        }
        
        xmlWriter.writeEndElement(); // iq
        xmlWriter.flush();
    }
}