package com.whatsapp.voice;

import com.whatsapp.net.SocketConnectionManager;
import com.whatsapp.protocol.Jid;
import com.whatsapp.protocol.KeyValue;
import com.whatsapp.protocol.ProtocolTreeNode;
import com.whatsapp.storage.AccountStore;

import javax.microedition.io.Connector;
import javax.microedition.io.Datagram;
import javax.microedition.io.DatagramConnection;

/**
 * WhatsApp VoIP Call Manager.
 * Implements call state machine:
 * - Signaling via <call><offer ...>, <call><accept ...>, <call><terminate ...>
 * - RTP / Datagram UDP audio streaming for voice call media over J2ME Connector datagram.
 * 
 * Modeled after com.whatsapp.infra.protocol.VoipStanzaChildNode reverse-engineered
 * with REA.
 */
public class VoiceCallManager implements Runnable {

    public static final int STATE_IDLE = 0;
    public static final int STATE_CALLING = 1;
    public static final int STATE_RINGING = 2;
    public static final int STATE_ACTIVE = 3;
    public static final int STATE_ENDED = 4;

    public interface CallListener {
        void onCallStateChanged(int state, Jid peerJid, String reason);
    }

    private int state = STATE_IDLE;
    private Jid activePeer;
    private String callId;
    private CallListener listener;

    private DatagramConnection udpConnection;
    private Thread udpThread;
    private volatile boolean streaming = false;

    private static VoiceCallManager instance;

    private VoiceCallManager() {}

    public static synchronized VoiceCallManager getInstance() {
        if (instance == null) {
            instance = new VoiceCallManager();
        }
        return instance;
    }

    public synchronized void setCallListener(CallListener l) {
        this.listener = l;
    }

    public synchronized int getState() {
        return state;
    }

    public synchronized Jid getActivePeer() {
        return activePeer;
    }

    public synchronized String getCallId() {
        return callId;
    }

    /**
     * Start an outgoing call to peerJid:
     * Sends <call to="peer"><offer call-id="..." .../></call>
     */
    public synchronized void startCall(Jid peerJid) {
        if (state != STATE_IDLE) return;
        this.activePeer = peerJid;
        this.callId = "call_" + System.currentTimeMillis();
        this.state = STATE_CALLING;
        notifyState(state, activePeer, "Calling...");

        try {
            KeyValue[] offerAttrs = new KeyValue[] {
                new KeyValue("call-id", callId),
                new KeyValue("call-creator", AccountStore.getInstance().getFullJidString()),
                new KeyValue("media", "audio"),
                new KeyValue("port", String.valueOf(AccountStore.getInstance().getCallPort()))
            };
            ProtocolTreeNode offerNode = new ProtocolTreeNode("offer", offerAttrs);

            KeyValue[] callAttrs = new KeyValue[] {
                new KeyValue("to", peerJid.getRawString()),
                new KeyValue("id", "call_stanza_" + System.currentTimeMillis())
            };
            ProtocolTreeNode callNode = new ProtocolTreeNode("call", callAttrs, offerNode);
            SocketConnectionManager.getInstance().sendNode(callNode);

            VoiceAudioEngine.playCallRingTone();
        } catch (Exception e) {
            endCall("Signaling failed: " + e.getMessage());
        }
    }

    /**
     * Accept an incoming call:
     * Sends <call to="peer"><accept call-id="..." .../></call>
     */
    public synchronized void acceptCall() {
        if (state != STATE_RINGING || activePeer == null) return;
        this.state = STATE_ACTIVE;
        notifyState(state, activePeer, "Connected");

        try {
            KeyValue[] acceptAttrs = new KeyValue[] {
                new KeyValue("call-id", callId),
                new KeyValue("port", String.valueOf(AccountStore.getInstance().getCallPort()))
            };
            ProtocolTreeNode acceptNode = new ProtocolTreeNode("accept", acceptAttrs);

            KeyValue[] callAttrs = new KeyValue[] {
                new KeyValue("to", activePeer.getRawString()),
                new KeyValue("id", "call_accept_" + System.currentTimeMillis())
            };
            ProtocolTreeNode callNode = new ProtocolTreeNode("call", callAttrs, acceptNode);
            SocketConnectionManager.getInstance().sendNode(callNode);

            startUdpAudioStream();
        } catch (Exception e) {
            endCall("Accept failed: " + e.getMessage());
        }
    }

    /**
     * Terminate or reject call:
     * Sends <call to="peer"><terminate call-id="..." .../></call>
     */
    public synchronized void endCall(String reason) {
        if (state == STATE_IDLE) return;
        this.state = STATE_ENDED;
        stopUdpAudioStream();

        if (activePeer != null) {
            try {
                KeyValue[] termAttrs = new KeyValue[] {
                    new KeyValue("call-id", callId != null ? callId : ""),
                    new KeyValue("reason", reason != null ? reason : "hangup")
                };
                ProtocolTreeNode termNode = new ProtocolTreeNode("terminate", termAttrs);

                KeyValue[] callAttrs = new KeyValue[] {
                    new KeyValue("to", activePeer.getRawString()),
                    new KeyValue("id", "call_term_" + System.currentTimeMillis())
                };
                ProtocolTreeNode callNode = new ProtocolTreeNode("call", callAttrs, termNode);
                SocketConnectionManager.getInstance().sendNode(callNode);
            } catch (Exception ignored) {}
        }

        notifyState(STATE_IDLE, activePeer, reason);
        this.activePeer = null;
        this.callId = null;
        this.state = STATE_IDLE;
    }

    /**
     * Handle incoming call stanza from network.
     */
    public synchronized void handleCallStanza(ProtocolTreeNode callNode) {
        if (callNode == null) return;
        String from = callNode.getAttribute("from");
        Jid peer = Jid.of(from);

        ProtocolTreeNode offer = callNode.getChild("offer");
        if (offer != null) {
            this.activePeer = peer;
            this.callId = offer.getAttribute("call-id");
            this.state = STATE_RINGING;
            notifyState(state, activePeer, "Incoming call...");
            VoiceAudioEngine.playCallRingTone();
            return;
        }

        ProtocolTreeNode accept = callNode.getChild("accept");
        if (accept != null) {
            this.state = STATE_ACTIVE;
            notifyState(state, activePeer, "Call active");
            startUdpAudioStream();
            return;
        }

        ProtocolTreeNode terminate = callNode.getChild("terminate");
        if (terminate != null) {
            String r = terminate.getAttribute("reason");
            endCall(r != null ? r : "Call ended by peer");
        }
    }

    private void startUdpAudioStream() {
        if (streaming) return;
        streaming = true;
        udpThread = new Thread(this);
        udpThread.start();
    }

    private void stopUdpAudioStream() {
        streaming = false;
        try {
            if (udpConnection != null) {
                udpConnection.close();
            }
        } catch (Exception ignored) {}
        udpConnection = null;
    }

    public void run() {
        AccountStore acc = AccountStore.getInstance();
        int port = acc.getCallPort();
        try {
            // Open local datagram connection on call port
            udpConnection = (DatagramConnection) Connector.open("datagram://:" + port);
            byte[] buf = new byte[1024];
            Datagram dg = udpConnection.newDatagram(buf, buf.length);

            while (streaming) {
                udpConnection.receive(dg);
                int len = dg.getLength();
                if (len > 0) {
                    byte[] audioPkt = new byte[len];
                    System.arraycopy(dg.getData(), 0, audioPkt, 0, len);
                    VoiceAudioEngine.getInstance().playAudio(audioPkt, "audio/amr");
                }
                dg.setLength(buf.length);
            }
        } catch (Exception e) {
            // UDP finished or closed
        }
    }

    private void notifyState(int s, Jid p, String r) {
        if (listener != null) {
            listener.onCallStateChanged(s, p, r);
        }
    }
}
