package me.forketyfork.growing.xmpp;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

public interface XmppMessageHandler {
    
    ClientContext handleMessageStanza(XMLStreamReader xmlReader, ClientContext context) throws XMLStreamException;
}