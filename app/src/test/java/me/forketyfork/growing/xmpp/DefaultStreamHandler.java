package me.forketyfork.growing.xmpp;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Default implementation of XmppStreamHandler.
 */
public class DefaultStreamHandler implements XmppStreamHandler {
    
    private final Logger logger = Logger.getLogger("DefaultStreamHandler");
    private final XmppServerConfig config;
    
    public DefaultStreamHandler(XmppServerConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("XmppServerConfig cannot be null");
        }
        this.config = config;
    }
    
    @Override
    public ClientContext handleStreamStart(XMLStreamReader xmlReader, ClientContext context) throws XMLStreamException {
        // xmlReader currently isn't used but may be needed for future stream validation
        logger.log(Level.FINE, "Stream start, currentState: {0}", context.getState());
        
        XMLStreamWriter xmlWriter = context.getXmlWriter();
        
        // Send XML declaration and stream header
        xmlWriter.writeStartDocument("UTF-8", "1.0");
        xmlWriter.writeStartElement("stream", "stream", XmppServerConfig.NAMESPACE_STREAM);
        xmlWriter.writeAttribute("from", config.serverName());
        xmlWriter.writeAttribute("id", "test-" + System.currentTimeMillis());
        xmlWriter.writeAttribute("version", "1.0");
        xmlWriter.writeDefaultNamespace(XmppServerConfig.NAMESPACE_CLIENT);
        xmlWriter.writeNamespace("stream", XmppServerConfig.NAMESPACE_STREAM);
        
        // Send features based on the current state
        if (context.getState() == ClientState.WAITING_FOR_STREAM_START) {
            sendSaslFeatures(xmlWriter);
            context.setState(ClientState.WAITING_FOR_AUTH);
        } else if (context.getState() == ClientState.AUTHENTICATED_WAITING_FOR_RESTART) {
            sendBindFeatures(xmlWriter);
            context.setState(ClientState.PROCESSING_STANZAS);
        }
        
        xmlWriter.flush();
        return context;
    }
    
    @Override
    public ClientContext handleStreamEnd(XMLStreamReader xmlReader, ClientContext context) throws XMLStreamException {
        // xmlReader currently isn't used but may be needed for future stream validation
        logger.log(Level.FINE, "Handling stream end, currentState: {0}", context.getState());
        XMLStreamWriter xmlWriter = context.getXmlWriter();
        
        xmlWriter.writeEndElement(); // Close the stream:stream element
        xmlWriter.writeEndDocument(); // Close the XML document
        xmlWriter.flush();
        
        context.setState(ClientState.CLOSED);
        return context;
    }
    
    private void sendSaslFeatures(XMLStreamWriter xmlWriter) throws XMLStreamException {
        xmlWriter.writeStartElement("stream", "features", XmppServerConfig.NAMESPACE_STREAM);
        xmlWriter.writeStartElement("mechanisms");
        xmlWriter.writeAttribute("xmlns", XmppServerConfig.NAMESPACE_SASL);
        xmlWriter.writeStartElement("mechanism");
        xmlWriter.writeCharacters("PLAIN");
        xmlWriter.writeEndElement(); // mechanism
        xmlWriter.writeEndElement(); // mechanisms
        xmlWriter.writeEndElement(); // features
        xmlWriter.flush();
    }
    
    private void sendBindFeatures(XMLStreamWriter xmlWriter) throws XMLStreamException {
        xmlWriter.writeStartElement("stream", "features", XmppServerConfig.NAMESPACE_STREAM);
        xmlWriter.writeStartElement("compression");
        xmlWriter.writeAttribute("xmlns", XmppServerConfig.NAMESPACE_COMPRESSION);
        xmlWriter.writeStartElement("method");
        xmlWriter.writeCharacters("zlib");
        xmlWriter.writeEndElement(); // method
        xmlWriter.writeEndElement(); // compression
        xmlWriter.writeEmptyElement("bind");
        xmlWriter.writeAttribute("xmlns", XmppServerConfig.NAMESPACE_BIND);
        xmlWriter.writeEmptyElement("session");
        xmlWriter.writeAttribute("xmlns", XmppServerConfig.NAMESPACE_SESSION);
        xmlWriter.writeEndElement(); // features
        xmlWriter.flush();
    }
}