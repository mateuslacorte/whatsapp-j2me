package com.whatsapp.net;

import com.whatsapp.model.ChatMessage;
import com.whatsapp.model.Contact;
import com.whatsapp.protocol.Jid;
import com.whatsapp.storage.AccountStore;
import com.whatsapp.storage.ChatStore;
import com.whatsapp.storage.SDCardMessageStore;
import com.whatsapp.util.Base64;
import com.whatsapp.util.SimpleJson;
import com.whatsapp.util.StringUtil;

import javax.microedition.io.Connector;
import javax.microedition.io.HttpConnection;
import javax.microedition.lcdui.Image;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.ByteArrayOutputStream;
import java.util.Vector;

/**
 * HTTP Client communicating with the Rust Baileys Bridge.
 * Enforces UTF-8 encoding so Portuguese accents and special characters render cleanly.
 * Provides instant REST syncing for chats and per-conversation messages.
 */
public class BaileysBridgeClient implements Runnable {

    private static BaileysBridgeClient instance;
    private boolean running = false;
    private Thread syncThread;
    private MessageListener listener;
    public static final String BROWSER_UA = "Mozilla/5.0 (BlackBerry; U; BlackBerry 9000; en) AppleWebKit/534.11";

    public interface MessageListener {
        void onNewMessage(ChatMessage message);
        void onCallReceived(String callerJid, String callId);
        void onSessionDisconnected();
    }

    private BaileysBridgeClient() {}

    public static synchronized BaileysBridgeClient getInstance() {
        if (instance == null) {
            instance = new BaileysBridgeClient();
        }
        return instance;
    }

    public void setMessageListener(MessageListener l) {
        this.listener = l;
    }

    public synchronized void startBackgroundSync() {
        if (!running) {
            running = true;
            syncThread = new Thread(this);
            syncThread.setPriority(Thread.MIN_PRIORITY);
            syncThread.start();
        }
    }

    public synchronized void stopBackgroundSync() {
        running = false;
        if (syncThread != null) {
            syncThread.interrupt();
            syncThread = null;
        }
    }

    public String checkStatus() {
        AccountStore acc = AccountStore.getInstance();
        String url = acc.getBaseUrl() + "/api/device/" + acc.getDeviceId() + "/status";
        System.out.println(">>> [STATUS CHECK] URL: " + url);
        HttpConnection conn = null;
        InputStream is = null;
        try {
            conn = NetworkUtil.openAndConnect(url, HttpConnection.GET, BROWSER_UA);
            int code = conn.getResponseCode();
            System.out.println(">>> [STATUS CHECK] Code: " + code);
            if (code == HttpConnection.HTTP_OK) {
                is = conn.openInputStream();
                String body = readStreamToString(is);
                Object parsed = SimpleJson.parse(body);
                if (parsed instanceof SimpleJson.JsonObject) {
                    SimpleJson.JsonObject json = (SimpleJson.JsonObject) parsed;
                    String status = json.getString("status");
                    boolean hasQr = json.getBoolean("has_qr", false);
                    if (hasQr || "qr".equals(status) || "close".equals(status) || "logged_out".equals(status) || "disconnected".equals(status)) {
                        return "disconnected";
                    }
                    return status != null ? status : "unknown";
                }
            } else if (code == 404 || code == 401 || code == 403) {
                return "disconnected";
            }
        } catch (Exception e) {
            System.out.println(">>> [STATUS CHECK EXCEPTION]: " + e.getClass().getName() + ": " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (is != null) { try { is.close(); } catch (Exception ignored) {} }
            if (conn != null) { try { conn.close(); } catch (Exception ignored) {} }
        }
        return "unknown";
    }

    /**
     * Instantly fetches the latest active chats from the Bridge server.
     */
    public boolean fetchChats() {
        AccountStore acc = AccountStore.getInstance();
        String url = acc.getBaseUrl() + "/api/device/" + acc.getDeviceId() + "/chats?limit=50";
        HttpConnection conn = null;
        InputStream is = null;
        try {
            conn = NetworkUtil.openAndConnect(url, HttpConnection.GET, BROWSER_UA);
            if (conn.getResponseCode() == HttpConnection.HTTP_OK) {
                is = conn.openInputStream();
                String body = readStreamToString(is);
                Object parsed = SimpleJson.parse(body);
                if (parsed instanceof SimpleJson.JsonObject) {
                    SimpleJson.JsonObject root = (SimpleJson.JsonObject) parsed;
                    SimpleJson.JsonArray arr = root.getArray("chats");
                    if (arr != null) {
                        int sz = arr.size();
                        Vector newContacts = new Vector();
                        for (int i = 0; i < sz; i++) {
                            SimpleJson.JsonObject ch = arr.getObject(i);
                            if (ch != null) {
                                String jid = ch.getString("jid");
                                String name = ch.getString("name");
                                String lastMsg = ch.getString("lastMessage");
                                int unread = ch.getInt("unread", 0);

                                if (jid != null && jid.length() > 0) {
                                    Contact c = new Contact(Jid.of(jid), (name != null && name.length() > 0) ? name : jid, "");
                                    if (lastMsg != null && lastMsg.length() > 0) {
                                        c.setStatusText(lastMsg);
                                    }
                                    if (ChatStore.getInstance().isConversationActive(Jid.of(jid))) {
                                        c.setUnreadCount(0);
                                    } else {
                                        c.setUnreadCount(unread);
                                    }
                                    newContacts.addElement(c);
                                }
                            }
                        }
                        ChatStore.getInstance().setContacts(newContacts);
                        if (listener != null) {
                            listener.onNewMessage(null);
                        }
                        return true;
                    }
                }
            }
        } catch (Exception e) {
        } finally {
            if (is != null) { try { is.close(); } catch (Exception ignored) {} }
            if (conn != null) { try { conn.close(); } catch (Exception ignored) {} }
        }
        return false;
    }

    /**
     * Fetches message history with lazy loading / pagination support.
     * If beforeTs <= 0, fetches the most recent batch (limit messages).
     * If beforeTs > 0, fetches older messages strictly before beforeTs.
     */
    public Vector fetchMessages(String jid, int limit, long beforeTs) {
        if (jid == null || jid.length() == 0) return null;
        AccountStore acc = AccountStore.getInstance();
        String url = acc.getBaseUrl() + "/api/device/" + acc.getDeviceId() + "/messages?jid=" + escapeUrl(jid) + "&limit=" + limit;
        if (beforeTs > 0) {
            url += "&before_ts=" + beforeTs;
        }
        HttpConnection conn = null;
        InputStream is = null;
        try {
            conn = NetworkUtil.openAndConnect(url, HttpConnection.GET, BROWSER_UA);
            if (conn.getResponseCode() == HttpConnection.HTTP_OK) {
                is = conn.openInputStream();
                String body = readStreamToString(is);
                Object parsed = SimpleJson.parse(body);
                if (parsed instanceof SimpleJson.JsonObject) {
                    SimpleJson.JsonObject root = (SimpleJson.JsonObject) parsed;
                    SimpleJson.JsonArray arr = root.getArray("messages");
                    if (arr != null) {
                        int sz = arr.size();
                        Vector list = new Vector();
                        for (int i = 0; i < sz; i++) {
                            SimpleJson.JsonObject m = arr.getObject(i);
                            if (m != null) {
                                String id = m.getString("id");
                                boolean fromMe = m.getBoolean("fromMe", false);
                                String text = m.getString("text");
                                long ts = m.getLong("timestamp", System.currentTimeMillis());

                                if (text != null && text.length() > 0) {
                                    ChatMessage cmsg = new ChatMessage(id, Jid.of(jid), text, ts, fromMe);
                                    boolean hasImg = m.getBoolean("hasImage", false);
                                    if (!hasImg && "[Imagem]".equals(text)) {
                                        hasImg = true;
                                    }
                                    cmsg.setHasImage(hasImg);
                                    String qId = m.getString("quotedId");
                                    String qText = m.getString("quotedText");
                                    String qSender = m.getString("quotedSender");
                                    if (qText != null && qText.length() > 0) {
                                        cmsg.setQuotedId(qId);
                                        cmsg.setQuotedText(qText);
                                        cmsg.setQuotedSender(qSender);
                                    }
                                    String part = m.getString("participant");
                                    String sName = m.getString("senderName");
                                    if (part != null && part.length() > 0) {
                                        cmsg.setParticipant(part);
                                    }
                                    if (sName != null && sName.length() > 0) {
                                        cmsg.setSenderName(sName);
                                    }
                                    list.addElement(cmsg);
                                }
                            }
                        }
                        if (beforeTs <= 0) {
                            ChatStore.getInstance().setMessages(Jid.of(jid), list);
                            SDCardMessageStore.getInstance().saveMessages(Jid.of(jid), list);
                        } else {
                            ChatStore.getInstance().prependOlderMessages(Jid.of(jid), list);
                        }
                        if (listener != null) {
                            listener.onNewMessage(null);
                        }
                        return list;
                    }
                }
            }
        } catch (Exception e) {
        } finally {
            if (is != null) { try { is.close(); } catch (Exception ignored) {} }
            if (conn != null) { try { conn.close(); } catch (Exception ignored) {} }
        }
        return null;
    }

    public boolean fetchMessages(String jid) {
        return fetchMessages(jid, 15, 0) != null;
    }

    public boolean sendMessage(String toJid, String text) {
        return sendMessage(toJid, text, null);
    }

    public boolean sendMessage(String toJid, String text, ChatMessage quotedMsg) {
        AccountStore acc = AccountStore.getInstance();
        String url = acc.getBaseUrl() + "/api/device/" + acc.getDeviceId() + "/send";
        HttpConnection conn = null;
        OutputStream os = null;
        try {
            conn = NetworkUtil.openHttp(url);
            conn.setRequestProperty("User-Agent", BROWSER_UA);
            conn.setRequestMethod(HttpConnection.POST);
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");

            StringBuffer sb = new StringBuffer();
            sb.append("{\"to\":\"").append(escapeJson(toJid)).append("\",\"text\":\"").append(escapeJson(text)).append("\"");
            if (quotedMsg != null) {
                if (quotedMsg.getId() != null) {
                    sb.append(",\"quoted_id\":\"").append(escapeJson(quotedMsg.getId())).append("\"");
                }
                if (quotedMsg.getText() != null) {
                    sb.append(",\"quoted_text\":\"").append(escapeJson(quotedMsg.getText())).append("\"");
                }
                sb.append(",\"quoted_from_me\":").append(quotedMsg.isFromMe() ? "true" : "false");
                String qPart = quotedMsg.getParticipant();
                if (qPart == null || qPart.length() == 0) {
                    if (quotedMsg.getRemoteJid() != null) {
                        qPart = quotedMsg.getRemoteJid().getRawString();
                    }
                }
                if (qPart != null && qPart.length() > 0) {
                    sb.append(",\"quoted_participant\":\"").append(escapeJson(qPart)).append("\"");
                }
                String qAuthor = quotedMsg.isFromMe() ? "Você" : 
                    (quotedMsg.getSenderName() != null && quotedMsg.getSenderName().length() > 0 ? quotedMsg.getSenderName() : 
                    (quotedMsg.getParticipant() != null ? StringUtil.formatPhoneNumber(quotedMsg.getParticipant()) : ""));
                if (qAuthor == null || qAuthor.length() == 0) {
                    qAuthor = quotedMsg.getQuotedSender();
                }
                qAuthor = StringUtil.formatPhoneNumber(qAuthor);
                if (qAuthor != null && qAuthor.length() > 0) {
                    sb.append(",\"quoted_sender\":\"").append(escapeJson(qAuthor)).append("\"");
                }
            }
            sb.append("}");

            String jsonPayload = sb.toString();
            byte[] bytes;
            try {
                bytes = jsonPayload.getBytes("UTF-8");
            } catch (Exception ex) {
                bytes = jsonPayload.getBytes();
            }
            os = conn.openOutputStream();
            os.write(bytes);
            os.flush();

            return conn.getResponseCode() == HttpConnection.HTTP_OK;
        } catch (Exception e) {
            return false;
        } finally {
            if (os != null) { try { os.close(); } catch (Exception ignored) {} }
            if (conn != null) { try { conn.close(); } catch (Exception ignored) {} }
        }
    }

    public boolean sendAudio(String toJid, byte[] audioBytes) {
        if (audioBytes == null || audioBytes.length == 0) return false;
        AccountStore acc = AccountStore.getInstance();
        String url = acc.getBaseUrl() + "/api/device/" + acc.getDeviceId() + "/send_audio";
        HttpConnection conn = null;
        OutputStream os = null;
        try {
            conn = NetworkUtil.openHttp(url);
            conn.setRequestProperty("User-Agent", BROWSER_UA);
            conn.setRequestMethod(HttpConnection.POST);
            conn.setRequestProperty("Content-Type", "application/json");

            String b64 = Base64.encode(audioBytes);
            String jsonPayload = "{\"to\":\"" + toJid + "\",\"base64\":\"" + b64 + "\",\"mimetype\":\"audio/amr\"}";
            byte[] bytes = jsonPayload.getBytes();
            os = conn.openOutputStream();
            os.write(bytes);
            os.flush();

            return conn.getResponseCode() == HttpConnection.HTTP_OK;
        } catch (Exception e) {
            return false;
        } finally {
            if (os != null) { try { os.close(); } catch (Exception ignored) {} }
            if (conn != null) { try { conn.close(); } catch (Exception ignored) {} }
        }
    }

    public void markChatRead(final String jid) {
        if (jid == null || jid.length() == 0) return;
        new Thread(new Runnable() {
            public void run() {
                AccountStore acc = AccountStore.getInstance();
                String url = acc.getBaseUrl() + "/api/device/" + acc.getDeviceId() + "/mark_read";
                HttpConnection conn = null;
                OutputStream os = null;
                try {
                    conn = NetworkUtil.openHttp(url);
                    conn.setRequestProperty("User-Agent", BROWSER_UA);
                    conn.setRequestMethod(HttpConnection.POST);
                    conn.setRequestProperty("Content-Type", "application/json");
                    String payload = "{\"jid\":\"" + jid + "\"}";
                    byte[] bytes = payload.getBytes("UTF-8");
                    os = conn.openOutputStream();
                    os.write(bytes);
                    os.flush();
                    conn.getResponseCode();
                } catch (Exception ignored) {
                } finally {
                    if (os != null) { try { os.close(); } catch (Exception ignored) {} }
                    if (conn != null) { try { conn.close(); } catch (Exception ignored) {} }
                }
            }
        }).start();
    }

    public void run() {
        long lastStatusCheck = System.currentTimeMillis();
        while (running) {
            AccountStore acc = AccountStore.getInstance();
            if (!acc.isRegistered()) {
                try { Thread.sleep(2000); } catch (Exception ignored) {}
                continue;
            }

            // Periodic status verification (every 25 seconds)
            long now = System.currentTimeMillis();
            if (now - lastStatusCheck > 25000) {
                lastStatusCheck = now;
                String st = checkStatus();
                if ("disconnected".equals(st)) {
                    if (listener != null) {
                        listener.onSessionDisconnected();
                    }
                    continue;
                }
            }

            String syncUrl = acc.getBaseUrl() + "/api/device/" + acc.getDeviceId() + "/sync";
            HttpConnection conn = null;
            InputStream is = null;
            try {
                conn = NetworkUtil.openAndConnect(syncUrl, HttpConnection.GET, BROWSER_UA);
                int resCode = conn.getResponseCode();
                if (resCode == HttpConnection.HTTP_OK) {
                    is = conn.openInputStream();
                    String body = readStreamToString(is);
                    if (body != null && body.length() > 0) {
                        Object parsed = SimpleJson.parse(body);
                        if (parsed instanceof SimpleJson.JsonObject) {
                            SimpleJson.JsonObject json = (SimpleJson.JsonObject) parsed;
                            String type = json.getString("type");

                            if ("snapshot".equals(type)) {
                                handleSnapshot(json);
                            } else if ("chats_update".equals(type)) {
                                handleChatsUpdate(json);
                            } else if ("message".equals(type)) {
                                handleSingleMessage(json);
                            } else if ("call".equals(type)) {
                                String from = json.getString("from");
                                String callId = json.getString("callId");
                                if (listener != null && from != null) {
                                    listener.onCallReceived(from, callId != null ? callId : "");
                                }
                            } else if ("connection".equals(type)) {
                                String st = json.getString("status");
                                boolean reconnect = json.getBoolean("reconnect", true);
                                if ("close".equals(st) && !reconnect) {
                                    if (listener != null) {
                                        listener.onSessionDisconnected();
                                    }
                                }
                            } else if ("qr".equals(type)) {
                                if (listener != null) {
                                    listener.onSessionDisconnected();
                                }
                            }
                        }
                    }
                } else if (resCode == 401 || resCode == 403 || resCode == 404) {
                    if (listener != null) {
                        listener.onSessionDisconnected();
                    }
                }
            } catch (Exception e) {
                try { Thread.sleep(1500); } catch (Exception ignored) {}
            } finally {
                if (is != null) { try { is.close(); } catch (Exception ignored) {} }
                if (conn != null) { try { conn.close(); } catch (Exception ignored) {} }
            }
        }
    }

    private void handleSnapshot(SimpleJson.JsonObject json) {
        SimpleJson.JsonArray chatsArr = json.getArray("chats");
        if (chatsArr != null) {
            int sz = chatsArr.size();
            for (int i = 0; i < sz; i++) {
                SimpleJson.JsonObject ch = chatsArr.getObject(i);
                if (ch != null) {
                    String jid = ch.getString("jid");
                    String name = ch.getString("name");
                    int unread = ch.getInt("unread", 0);
                    String lastMsg = ch.getString("lastMessage");
                    if (jid != null && jid.length() > 0) {
                        Contact c = new Contact(Jid.of(jid), name != null ? name : jid, "");
                        if (ChatStore.getInstance().isConversationActive(Jid.of(jid))) {
                            c.setUnreadCount(0);
                        } else {
                            c.setUnreadCount(unread);
                        }
                        if (lastMsg != null && lastMsg.length() > 0) {
                            c.setStatusText(lastMsg);
                        }
                        ChatStore.getInstance().addContact(c);
                    }
                }
            }
        }

        if (listener != null) {
            listener.onNewMessage(null);
        }
    }

    private void handleChatsUpdate(SimpleJson.JsonObject json) {
        SimpleJson.JsonArray chatsArr = json.getArray("chats");
        if (chatsArr != null) {
            for (int i = 0; i < chatsArr.size(); i++) {
                SimpleJson.JsonObject ch = chatsArr.getObject(i);
                if (ch != null) {
                    String jid = ch.getString("jid");
                    String name = ch.getString("name");
                    int unread = ch.getInt("unread", 0);
                    String lastMsg = ch.getString("lastMessage");
                    if (jid != null && jid.length() > 0) {
                        Contact c = new Contact(Jid.of(jid), name != null ? name : jid, "");
                        if (lastMsg != null && lastMsg.length() > 0) c.setStatusText(lastMsg);
                        if (ChatStore.getInstance().isConversationActive(Jid.of(jid))) {
                            c.setUnreadCount(0);
                        } else {
                            c.setUnreadCount(unread);
                        }
                        ChatStore.getInstance().addContact(c);
                    }
                }
            }
            if (listener != null) {
                listener.onNewMessage(null);
            }
        }
    }

    private void handleSingleMessage(SimpleJson.JsonObject json) {
        String from = json.getString("from");
        String text = json.getString("text");
        String id = json.getString("id");
        String senderName = json.getString("name");
        boolean fromMe = json.getBoolean("fromMe", false);
        long timestamp = json.getLong("timestamp", System.currentTimeMillis());

        if (from != null && from.length() > 0) {
            if (text == null) text = "";
            if (id == null) id = "msg_" + timestamp;
            ChatMessage msg = new ChatMessage(id, Jid.of(from), text, timestamp, fromMe);
            boolean hasImg = json.getBoolean("hasImage", false);
            if (!hasImg && "[Imagem]".equals(text)) {
                hasImg = true;
            }
            msg.setHasImage(hasImg);
            String qId = json.getString("quotedId");
            String qText = json.getString("quotedText");
            String qSender = json.getString("quotedSender");
            if (qText != null && qText.length() > 0) {
                msg.setQuotedId(qId);
                msg.setQuotedText(qText);
                msg.setQuotedSender(qSender);
            }
            String part = json.getString("participant");
            String sName = json.getString("senderName");
            if (part != null && part.length() > 0) {
                msg.setParticipant(part);
            }
            if (sName != null && sName.length() > 0) {
                msg.setSenderName(sName);
            }
            boolean added = ChatStore.getInstance().addMessage(msg);
            if (added) {
                SDCardMessageStore.getInstance().appendMessage(msg);
            }
            ChatStore.getInstance().bumpContactToTop(Jid.of(from), text);

            if (senderName != null && senderName.length() > 0 && from.indexOf("@g.us") == -1) {
                Contact c = ChatStore.getInstance().getContact(Jid.of(from));
                if (c != null && (c.getName() == null || c.getName().equals(c.getJid().getUser()))) {
                    c.setName(senderName);
                }
            }

            if (listener != null) {
                listener.onNewMessage(msg);
            }
        }
    }

    public Image fetchImage(String msgId, int maxW) {
        if (msgId == null || msgId.length() == 0) return null;
        AccountStore acc = AccountStore.getInstance();
        String url = acc.getBaseUrl() + "/api/device/" + acc.getDeviceId() + "/media/" + escapeUrl(msgId) + "?w=" + maxW;
        HttpConnection conn = null;
        InputStream is = null;
        try {
            conn = NetworkUtil.openAndConnect(url, HttpConnection.GET, BROWSER_UA);
            if (conn.getResponseCode() == HttpConnection.HTTP_OK) {
                is = conn.openInputStream();
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buf = new byte[2048];
                int r;
                while ((r = is.read(buf)) != -1) {
                    baos.write(buf, 0, r);
                }
                byte[] imgBytes = baos.toByteArray();
                if (imgBytes != null && imgBytes.length > 0) {
                    return Image.createImage(imgBytes, 0, imgBytes.length);
                }
            }
        } catch (Exception e) {
        } finally {
            if (is != null) { try { is.close(); } catch (Exception ignored) {} }
            if (conn != null) { try { conn.close(); } catch (Exception ignored) {} }
        }
        return null;
    }

    private String readStreamToString(InputStream is) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[2048];
        int read;
        while ((read = is.read(buf)) != -1) {
            baos.write(buf, 0, read);
        }
        byte[] bytes = baos.toByteArray();
        try {
            return SimpleJson.fixMojibake(new String(bytes, "UTF-8"));
        } catch (Exception e) {
            return SimpleJson.fixMojibake(new String(bytes));
        }
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"') sb.append("\\\"");
            else if (c == '\\') sb.append("\\\\");
            else if (c == '\n') sb.append("\\n");
            else if (c == '\r') sb.append("\\r");
            else sb.append(c);
        }
        return sb.toString();
    }

    private String escapeUrl(String s) {
        if (s == null) return "";
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '@') sb.append("%40");
            else if (c == ':') sb.append("%3A");
            else if (c == ' ') sb.append("%20");
            else sb.append(c);
        }
        return sb.toString();
    }
}
