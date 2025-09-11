package me.forketyfork.growing.xmpp;

import javax.xml.stream.XMLStreamWriter;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Context object that carries client state and connection information through the processing pipeline.
 */
public class ClientContext {
    private ClientState state;
    private String username;
    private String fullJid;
    private String bareJid;
    private final XMLStreamWriter xmlWriter;
    private final ConcurrentHashMap<String, ClientSession> clientRegistry;
    private final Set<String> connectedUsernames;

    public ClientContext(ClientState initialState, XMLStreamWriter xmlWriter,
                         ConcurrentHashMap<String, ClientSession> clientRegistry,
                         Set<String> connectedUsernames) {
        this.state = initialState;
        this.xmlWriter = xmlWriter;
        this.clientRegistry = clientRegistry;
        this.connectedUsernames = connectedUsernames;
    }

    public ClientState getState() {
        return state;
    }

    public void setState(ClientState state) {
        this.state = state;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getFullJid() {
        return fullJid;
    }

    public void setFullJid(String fullJid) {
        this.fullJid = fullJid;
        // Extract bare JID from full JID
        if (fullJid != null) {
            int resourceIndex = fullJid.indexOf('/');
            this.bareJid = resourceIndex >= 0 ? fullJid.substring(0, resourceIndex) : fullJid;
        }
    }

    public String getBareJid() {
        return bareJid;
    }

    public XMLStreamWriter getXmlWriter() {
        return xmlWriter;
    }

    public ConcurrentHashMap<String, ClientSession> getClientRegistry() {
        return clientRegistry;
    }

    public Set<String> getConnectedUsernames() {
        return connectedUsernames;
    }

    /**
     * Register this client in the global registry once JID is assigned.
     */
    public void registerClient() {
        if (fullJid != null && username != null) {
            ClientSession session = new ClientSession(username, fullJid, bareJid, xmlWriter);
            clientRegistry.put(fullJid, session);
            // Also, register by bare JID for an easier lookup
            clientRegistry.put(bareJid, session);
            // Add username to the connected usernames set for efficient duplicate checking
            connectedUsernames.add(username);
        }
    }

    /**
     * Find a client session by JID (either full or bare JID).
     */
    public ClientSession findClientSession(String jid) {
        return clientRegistry.get(jid);
    }
}