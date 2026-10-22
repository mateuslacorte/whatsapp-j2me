package com.whatsapp.net;

import com.whatsapp.protocol.KeyValue;
import com.whatsapp.protocol.ProtocolTreeNode;
import com.whatsapp.storage.AccountStore;
import com.whatsapp.util.StringUtil;

import javax.microedition.io.Connector;
import javax.microedition.io.SocketConnection;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Vector;

/**
 * WhatsApp Binary Stream Socket Manager.
 * Handles the low-level connection, protocol handshake, stream reader/writer,
 * and stanza routing over CLDC 1.1 SocketConnection.
 * 
 * Based on the reverse-engineered WhatsApp Android networking:
 * - WAHucClient / MNSStreamRuntime
 * - ProtocolJniHelper / ProtocolTreeNode
 */
public class SocketConnectionManager implements Runnable {

    public static final String WA_SERVER_DOMAIN = "s.whatsapp.net";
    public static final String WA_VERSION = "2.26.41.71";
    public static final String WA_USER_AGENT = "WhatsApp/2.26.41.71 BlackBerry/9900 J2ME/CLDC-1.1";

    private SocketConnection socket;
    private InputStream in;
    private OutputStream out;
    private Thread workerThread;
    private volatile boolean running = false;
    private Vector listeners = new Vector();

    private static SocketConnectionManager instance;

    private SocketConnectionManager() {}

    public static synchronized SocketConnectionManager getInstance() {
        if (instance == null) {
            instance = new SocketConnectionManager();
        }
        return instance;
    }

    public synchronized void addConnectionListener(ConnectionListener l) {
        if (l != null && !listeners.contains(l)) {
            listeners.addElement(l);
        }
    }

    public synchronized void removeConnectionListener(ConnectionListener l) {
        if (l != null) {
            listeners.removeElement(l);
        }
    }

    public synchronized boolean isConnected() {
        return running && socket != null;
    }

    public synchronized void connect() {
        if (running) return;
        running = true;
        workerThread = new Thread(this);
        workerThread.start();
    }

    public synchronized void disconnect() {
        running = false;
        closeStreams();
        notifyDisconnected("User disconnected");
    }

    private void closeStreams() {
        try { if (in != null) in.close(); } catch (Exception ignored) {}
        try { if (out != null) out.close(); } catch (Exception ignored) {}
        try { if (socket != null) socket.close(); } catch (Exception ignored) {}
        in = null;
        out = null;
        socket = null;
    }

    public void run() {
        AccountStore acc = AccountStore.getInstance();
        String host = acc.getServerHost();
        int port = acc.getServerPort();

        try {
            socket = NetworkUtil.openSocket(host, port);
            socket.setSocketOption(SocketConnection.KEEPALIVE, 1);
            socket.setSocketOption(SocketConnection.DELAY, 0);

            in = socket.openInputStream();
            out = socket.openOutputStream();

            notifyConnected();

            // Send WhatsApp Stream Open Handshake Stanza
            sendStreamOpen(acc);

            // Read incoming frames in loop
            while (running) {
                ProtocolTreeNode node = readNode();
                if (node != null) {
                    handleIncomingNode(node);
                }
            }
        } catch (Exception e) {
            if (running) {
                notifyError("Connection failed: " + e.getMessage());
                notifyDisconnected(e.getMessage());
            }
        } finally {
            closeStreams();
            running = false;
        }
    }

    /**
     * Send opening stream stanza:
     * <stream:features to="s.whatsapp.net" domain="s.whatsapp.net" version="1.0" ... />
     */
    private void sendStreamOpen(AccountStore acc) throws Exception {
        KeyValue[] attrs = new KeyValue[] {
            new KeyValue("to", WA_SERVER_DOMAIN),
            new KeyValue("domain", WA_SERVER_DOMAIN),
            new KeyValue("resource", "BlackBerry"),
            new KeyValue("version", "1.0"),
            new KeyValue("user-agent", WA_USER_AGENT),
            new KeyValue("user", acc.getCountryCode() + acc.getPhoneNumber())
        };
        ProtocolTreeNode streamOpen = new ProtocolTreeNode("stream:features", attrs);
        sendNode(streamOpen);

        // Send auth credential node
        sendAuth(acc);
    }

    private void sendAuth(AccountStore acc) throws Exception {
        KeyValue[] attrs = new KeyValue[] {
            new KeyValue("mechanism", "PLAIN"),
            new KeyValue("user", acc.getCountryCode() + acc.getPhoneNumber()),
            new KeyValue("token", acc.getAuthToken())
        };
        ProtocolTreeNode authNode = new ProtocolTreeNode("auth", attrs);
        sendNode(authNode);
    }

    /**
     * Write a protocol stanza to the output stream.
     */
    public synchronized void sendNode(ProtocolTreeNode node) throws Exception {
        if (out == null || node == null) return;
        String xml = node.toXmlString() + "\n";
        byte[] bytes = xml.getBytes("UTF-8");
        out.write(bytes);
        out.flush();
    }

    /**
     * Read an incoming protocol node from the stream.
     * Simple XML-framed reader for CLDC socket stream.
     */
    private ProtocolTreeNode readNode() throws Exception {
        if (in == null) return null;
        StringBuffer sb = new StringBuffer();
        int c;
        boolean inTag = false;
        while ((c = in.read()) != -1) {
            char ch = (char) c;
            sb.append(ch);
            if (ch == '<') {
                inTag = true;
            } else if (ch == '>') {
                // Check if this closes the root tag
                String curr = sb.toString().trim();
                if (curr.endsWith("/>") || (curr.indexOf("</") != -1 && curr.endsWith(">"))) {
                    return parseSimpleXmlNode(curr);
                }
            }
            if (sb.length() > 65536) {
                // Safeguard against oversized buffer on low-memory device
                break;
            }
        }
        if (c == -1) {
            running = false;
        }
        return null;
    }

    /**
     * Parses an XML stanza string into a ProtocolTreeNode.
     */
    private ProtocolTreeNode parseSimpleXmlNode(String xml) {
        if (xml == null || xml.length() == 0) return null;
        xml = xml.trim();
        if (!xml.startsWith("<") || !xml.endsWith(">")) return null;

        try {
            int tagEnd = xml.indexOf(' ');
            int closeBracket = xml.indexOf('>');
            if (tagEnd == -1 || tagEnd > closeBracket) {
                tagEnd = closeBracket;
                if (xml.endsWith("/>") && tagEnd > 1 && xml.charAt(tagEnd - 1) == '/') {
                    tagEnd--;
                }
            }
            String tag = xml.substring(1, tagEnd);

            // Parse attributes
            Vector attrs = new Vector();
            int attrStart = tagEnd;
            int attrEnd = xml.endsWith("/>") ? (xml.length() - 2) : closeBracket;
            if (attrStart < attrEnd) {
                String attrStr = xml.substring(attrStart, attrEnd);
                parseAttributes(attrStr, attrs);
            }

            KeyValue[] kvArr = new KeyValue[attrs.size()];
            for (int i = 0; i < attrs.size(); i++) {
                kvArr[i] = (KeyValue) attrs.elementAt(i);
            }

            // Parse body / children
            if (xml.endsWith("/>")) {
                return new ProtocolTreeNode(tag, kvArr);
            }

            int contentStart = closeBracket + 1;
            int contentEnd = StringUtil.lastIndexOf(xml, "</");
            if (contentEnd > contentStart) {
                String content = xml.substring(contentStart, contentEnd);
                if (content.indexOf('<') != -1) {
                    // Child node
                    ProtocolTreeNode child = parseSimpleXmlNode(content);
                    if (child != null) {
                        return new ProtocolTreeNode(tag, kvArr, child);
                    }
                }
                return new ProtocolTreeNode(tag, kvArr, content);
            }

            return new ProtocolTreeNode(tag, kvArr);
        } catch (Exception e) {
            return new ProtocolTreeNode("raw", null, xml);
        }
    }

    private void parseAttributes(String attrStr, Vector attrs) {
        String[] parts = StringUtil.split(attrStr, ' ');
        for (int i = 0; i < parts.length; i++) {
            String p = parts[i].trim();
            int eq = p.indexOf('=');
            if (eq > 0) {
                String k = p.substring(0, eq).trim();
                String v = p.substring(eq + 1).trim();
                if (v.startsWith("\"") && v.endsWith("\"") && v.length() >= 2) {
                    v = v.substring(1, v.length() - 1);
                } else if (v.startsWith("'") && v.endsWith("'") && v.length() >= 2) {
                    v = v.substring(1, v.length() - 1);
                }
                attrs.addElement(new KeyValue(k, v));
            }
        }
    }

    private void handleIncomingNode(ProtocolTreeNode node) {
        String tag = node.getTag();
        if ("success".equals(tag)) {
            notifyAuthenticated();
        } else if ("failure".equals(tag)) {
            String reason = node.getAttribute("reason");
            notifyAuthFailed(reason != null ? reason : "Authentication failed");
        }
        notifyNodeReceived(node);
    }

    private void notifyConnected() {
        for (int i = 0; i < listeners.size(); i++) {
            ((ConnectionListener) listeners.elementAt(i)).onConnected();
        }
    }

    private void notifyDisconnected(String reason) {
        for (int i = 0; i < listeners.size(); i++) {
            ((ConnectionListener) listeners.elementAt(i)).onDisconnected(reason);
        }
    }

    private void notifyAuthenticated() {
        for (int i = 0; i < listeners.size(); i++) {
            ((ConnectionListener) listeners.elementAt(i)).onAuthenticated();
        }
    }

    private void notifyAuthFailed(String reason) {
        for (int i = 0; i < listeners.size(); i++) {
            ((ConnectionListener) listeners.elementAt(i)).onAuthFailed(reason);
        }
    }

    private void notifyNodeReceived(ProtocolTreeNode node) {
        for (int i = 0; i < listeners.size(); i++) {
            ((ConnectionListener) listeners.elementAt(i)).onNodeReceived(node);
        }
    }

    private void notifyError(String err) {
        for (int i = 0; i < listeners.size(); i++) {
            ((ConnectionListener) listeners.elementAt(i)).onError(err);
        }
    }
}
