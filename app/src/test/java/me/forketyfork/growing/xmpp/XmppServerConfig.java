package me.forketyfork.growing.xmpp;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Configuration class for the SimpleXmppServer.
 */
@SuppressWarnings("HttpUrlsUsage")
public record XmppServerConfig(
        int port,
        String serverName,
        int socketTimeoutMs,
        int shutdownTimeoutMs,
        int maxConnections,
        Map<String, String> userCredentials
) {

    public static final String DEFAULT_SERVER_NAME = "localhost";
    public static final int DEFAULT_SOCKET_TIMEOUT_MS = 30000;
    public static final int DEFAULT_SHUTDOWN_TIMEOUT_MS = 5000;
    public static final int DEFAULT_MAX_CONNECTIONS = 100;

    // XMPP Namespaces
    public static final String NAMESPACE_STREAM = "http://etherx.jabber.org/streams";
    public static final String NAMESPACE_CLIENT = "jabber:client";
    public static final String NAMESPACE_SASL = "urn:ietf:params:xml:ns:xmpp-sasl";
    public static final String NAMESPACE_BIND = "urn:ietf:params:xml:ns:xmpp-bind";
    public static final String NAMESPACE_SESSION = "urn:ietf:params:xml:ns:xmpp-session";
    public static final String NAMESPACE_COMPRESSION = "http://jabber.org/features/compress";
    public static final String NAMESPACE_IQ_AUTH = "jabber:iq:auth";
    public static final String NAMESPACE_IQ_ROSTER = "jabber:iq:roster";

    /**
     * Create configuration with default settings.
     *
     * @param port the server port
     */
    public XmppServerConfig(int port) {
        this(port, DEFAULT_SERVER_NAME, DEFAULT_SOCKET_TIMEOUT_MS,
                DEFAULT_SHUTDOWN_TIMEOUT_MS, DEFAULT_MAX_CONNECTIONS,
                Collections.emptyMap());
    }

    /**
     * Create a configuration with custom settings.
     *
     * @param port              the server port
     * @param serverName        the server name (cannot be null or empty)
     * @param socketTimeoutMs   socket timeout in milliseconds (>= 0)
     * @param shutdownTimeoutMs shutdown timeout in milliseconds (>= 0)
     * @param maxConnections    maximum concurrent connections (> 0)
     * @throws IllegalArgumentException if any parameter is invalid
     */
    public XmppServerConfig(int port, String serverName, int socketTimeoutMs,
                            int shutdownTimeoutMs, int maxConnections,
                            Map<String, String> userCredentials) {
        if (port <= 0 || port > 65535) {
            throw new IllegalArgumentException("Port must be between 1 and 65535, got: " + port);
        }
        if (serverName == null || serverName.trim().isEmpty()) {
            throw new IllegalArgumentException("Server name cannot be null or empty");
        }
        if (socketTimeoutMs < 0) {
            throw new IllegalArgumentException("Socket timeout must be non-negative, got: " + socketTimeoutMs);
        }
        if (shutdownTimeoutMs < 0) {
            throw new IllegalArgumentException("Shutdown timeout must be non-negative, got: " + shutdownTimeoutMs);
        }
        if (maxConnections <= 0) {
            throw new IllegalArgumentException("Max connections must be positive, got: " + maxConnections);
        }
        Map<String, String> creds = userCredentials == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new HashMap<>(userCredentials));

        this.port = port;
        this.serverName = serverName.trim();
        this.socketTimeoutMs = socketTimeoutMs;
        this.shutdownTimeoutMs = shutdownTimeoutMs;
        this.maxConnections = maxConnections;
        this.userCredentials = creds;
    }

    /**
     * @return the server port
     */
    @Override
    public int port() {
        return port;
    }

    /**
     * @return the server name
     */
    @Override
    public String serverName() {
        return serverName;
    }

    /**
     * @return the socket timeout in milliseconds
     */
    @Override
    public int socketTimeoutMs() {
        return socketTimeoutMs;
    }

    /**
     * @return the shutdown timeout in milliseconds
     */
    @Override
    public int shutdownTimeoutMs() {
        return shutdownTimeoutMs;
    }

    /**
     * @return the maximum number of concurrent connections
     */
    @Override
    public int maxConnections() {
        return maxConnections;
    }

    /**
     * @return immutable map of allowed user credentials
     */
    @Override
    public Map<String, String> userCredentials() {
        return userCredentials;
    }

    /**
     * Builder for {@link XmppServerConfig}.
     */
    public static class Builder {
        private int port;
        private String serverName = DEFAULT_SERVER_NAME;
        private int socketTimeoutMs = DEFAULT_SOCKET_TIMEOUT_MS;
        private int shutdownTimeoutMs = DEFAULT_SHUTDOWN_TIMEOUT_MS;
        private int maxConnections = DEFAULT_MAX_CONNECTIONS;
        private final Map<String, String> users = new HashMap<>();

        public Builder port(int port) {
            this.port = port;
            return this;
        }

        public Builder serverName(String serverName) {
            this.serverName = serverName;
            return this;
        }

        public Builder socketTimeoutMs(int socketTimeoutMs) {
            this.socketTimeoutMs = socketTimeoutMs;
            return this;
        }

        public Builder shutdownTimeoutMs(int shutdownTimeoutMs) {
            this.shutdownTimeoutMs = shutdownTimeoutMs;
            return this;
        }

        public Builder maxConnections(int maxConnections) {
            this.maxConnections = maxConnections;
            return this;
        }

        public Builder addUser(String username, String password) {
            if (username != null && password != null) {
                users.put(username, password);
            }
            return this;
        }

        public XmppServerConfig build() {
            return new XmppServerConfig(port, serverName, socketTimeoutMs,
                    shutdownTimeoutMs, maxConnections, users);
        }
    }

    /**
     * Create a builder for the configuration.
     */
    public static Builder builder() {
        return new Builder();
    }
}