package me.forketyfork.growing.xmpp;

/**
 * XMPP client connection states for tracking the authentication flow.
 */
public enum ClientState {
    WAITING_FOR_STREAM_START,
    WAITING_FOR_AUTH,
    AUTHENTICATED_WAITING_FOR_RESTART,
    PROCESSING_STANZAS,
    CLOSED
}