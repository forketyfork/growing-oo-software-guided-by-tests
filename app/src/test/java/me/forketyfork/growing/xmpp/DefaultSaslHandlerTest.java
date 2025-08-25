package me.forketyfork.growing.xmpp;

import org.junit.jupiter.api.Test;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

public class DefaultSaslHandlerTest {

    private XMLStreamReader createReader(String payload) throws Exception {
        String xml = "<auth xmlns='" + XmppServerConfig.NAMESPACE_SASL + "' mechanism='PLAIN'>" + payload + "</auth>";
        return XMLInputFactory.newFactory().createXMLStreamReader(new StringReader(xml));
    }

    private XMLStreamWriter createWriter(StringWriter out) throws Exception {
        return XMLOutputFactory.newFactory().createXMLStreamWriter(out);
    }

    private ClientContext createContext(XMLStreamWriter writer) {
        return new ClientContext(ClientState.WAITING_FOR_AUTH, writer, new ConcurrentHashMap<>());
    }

    @Test
    public void authenticatesKnownUser() throws Exception {
        String creds = Base64.getEncoder().encodeToString("\0user\0pass".getBytes());
        XMLStreamReader reader = createReader(creds);
        reader.nextTag();
        StringWriter out = new StringWriter();
        XMLStreamWriter writer = createWriter(out);
        ClientContext ctx = createContext(writer);
        DefaultSaslHandler handler = new DefaultSaslHandler(Map.of("user", "pass"));

        handler.handleSaslAuth(reader, ctx);

        assertEquals(ClientState.AUTHENTICATED_WAITING_FOR_RESTART, ctx.getState());
        assertTrue(out.toString().contains("<success"));
    }

    @Test
    public void rejectsUnknownUser() throws Exception {
        String creds = Base64.getEncoder().encodeToString("\0user\0wrong".getBytes());
        XMLStreamReader reader = createReader(creds);
        reader.nextTag();
        StringWriter out = new StringWriter();
        XMLStreamWriter writer = createWriter(out);
        ClientContext ctx = createContext(writer);
        DefaultSaslHandler handler = new DefaultSaslHandler(Map.of("user", "pass"));

        handler.handleSaslAuth(reader, ctx);

        assertEquals(ClientState.CLOSED, ctx.getState());
        assertTrue(out.toString().contains("<failure"));
    }
}
