package com.whatsapp.net;

import com.whatsapp.protocol.ProtocolTreeNode;

/**
 * Listener interface for connection and stanza events.
 */
public interface ConnectionListener {
    void onConnected();
    void onDisconnected(String reason);
    void onAuthenticated();
    void onAuthFailed(String reason);
    void onNodeReceived(ProtocolTreeNode node);
    void onError(String message);
}
