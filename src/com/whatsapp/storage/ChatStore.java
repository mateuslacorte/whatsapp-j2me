package com.whatsapp.storage;

import com.whatsapp.model.ChatMessage;
import com.whatsapp.model.Contact;
import com.whatsapp.protocol.Jid;
import com.whatsapp.util.StringUtil;

import java.util.Hashtable;
import java.util.Vector;

/**
 * In-memory message and contact store with real-time sync from Baileys and SDCard/RMS persistence.
 * Features deduplication by message ID and content, LID normalization, and contact name preservation.
 * Guarantees strict chronological ordering for all conversation messages.
 */
public class ChatStore {

    private static ChatStore instance;
    private Hashtable conversations = new Hashtable(); // Jid user -> Vector of ChatMessage
    private Vector contacts = new Vector(); // Vector of Contact
    private Jid activeConversationJid;

    private ChatStore() {}

    public static synchronized ChatStore getInstance() {
        if (instance == null) {
            instance = new ChatStore();
        }
        return instance;
    }

    public synchronized void setActiveConversation(Jid jid) {
        this.activeConversationJid = jid;
        if (jid != null) {
            clearUnread(jid);
        }
    }

    public synchronized boolean isConversationActive(Jid jid) {
        if (activeConversationJid == null || jid == null) return false;
        String cleanA = normalizeUser(activeConversationJid.getUser());
        String cleanB = normalizeUser(jid.getUser());
        return cleanA.equals(cleanB) || activeConversationJid.equals(jid);
    }

    public synchronized void clearUnread(Jid jid) {
        if (jid == null) return;
        String cleanUser = normalizeUser(jid.getUser());
        for (int i = 0; i < contacts.size(); i++) {
            Contact c = (Contact) contacts.elementAt(i);
            if (normalizeUser(c.getJid().getUser()).equals(cleanUser) || c.getJid().equals(jid)) {
                c.setUnreadCount(0);
                break;
            }
        }
    }

    public synchronized void addContact(Contact c) {
        if (c == null || c.getJid() == null) return;
        String cleanUser = normalizeUser(c.getJid().getUser());

        for (int i = 0; i < contacts.size(); i++) {
            Contact existing = (Contact) contacts.elementAt(i);
            String existingUser = normalizeUser(existing.getJid().getUser());

            if (existingUser.equals(cleanUser) || existing.getJid().equals(c.getJid())) {
                if (c.getName() != null && c.getName().trim().length() > 0 && !c.getName().equals(cleanUser)) {
                    String newName = c.getName().trim();
                    boolean existingHasRealName = existing.getName() != null 
                        && existing.getName().trim().length() > 0 
                        && !StringUtil.isNumericOrPhone(existing.getName());
                    boolean newHasRealName = !StringUtil.isNumericOrPhone(newName);

                    if (newHasRealName || !existingHasRealName) {
                        existing.setName(newName);
                    }
                }
                if (c.getStatusText() != null && c.getStatusText().length() > 0) {
                    existing.setStatusText(c.getStatusText());
                }
                if (!isConversationActive(c.getJid())) {
                    existing.setUnreadCount(c.getUnreadCount());
                } else {
                    existing.setUnreadCount(0);
                }
                return;
            }
        }
        contacts.addElement(c);
    }

    public synchronized void setContacts(Vector newContacts) {
        if (newContacts != null) {
            this.contacts = newContacts;
        }
    }

    public synchronized void bumpContactToTop(Jid jid, String lastMsg) {
        if (jid == null) return;
        String cleanUser = normalizeUser(jid.getUser());
        Contact found = null;
        for (int i = 0; i < contacts.size(); i++) {
            Contact c = (Contact) contacts.elementAt(i);
            if (normalizeUser(c.getJid().getUser()).equals(cleanUser) || c.getJid().equals(jid)) {
                found = c;
                contacts.removeElementAt(i);
                break;
            }
        }
        if (found == null) {
            found = new Contact(jid, cleanUser, "");
        }
        if (lastMsg != null && lastMsg.length() > 0) {
            found.setStatusText(lastMsg);
        }
        contacts.insertElementAt(found, 0);
    }

    public synchronized Vector getContacts() {
        return contacts;
    }

    public synchronized Contact getContact(Jid jid) {
        if (jid == null) return null;
        String cleanUser = normalizeUser(jid.getUser());

        for (int i = 0; i < contacts.size(); i++) {
            Contact c = (Contact) contacts.elementAt(i);
            String existingUser = normalizeUser(c.getJid().getUser());
            if (existingUser.equals(cleanUser) || c.getJid().equals(jid)) {
                return c;
            }
        }
        Contact newContact = new Contact(jid, cleanUser, "");
        contacts.addElement(newContact);
        return newContact;
    }

    /**
     * Sets or replaces messages for a conversation with a sorted list.
     */
    public synchronized void setMessages(Jid jid, Vector newMsgs) {
        if (jid == null) return;
        String key = normalizeUser(jid.getUser());

        Vector existing = (Vector) conversations.get(key);
        if (existing == null || existing.isEmpty()) {
            existing = SDCardMessageStore.getInstance().loadMessages(jid);
        }

        if (existing != null && newMsgs != null) {
            for (int i = 0; i < newMsgs.size(); i++) {
                ChatMessage n = (ChatMessage) newMsgs.elementAt(i);
                if (!n.hasQuote()) {
                    for (int j = 0; j < existing.size(); j++) {
                        ChatMessage ex = (ChatMessage) existing.elementAt(j);
                        if (ex.hasQuote()) {
                            boolean match = (n.getId() != null && n.getId().length() > 0 && n.getId().equals(ex.getId()))
                                || (n.isFromMe() == ex.isFromMe() && n.getText() != null && n.getText().equals(ex.getText()) && Math.abs(n.getTimestamp() - ex.getTimestamp()) < 25000);
                            if (match) {
                                n.setQuotedId(ex.getQuotedId());
                                n.setQuotedText(ex.getQuotedText());
                                n.setQuotedSender(ex.getQuotedSender());
                                break;
                            }
                        }
                    }
                }
            }
        }

        conversations.put(key, newMsgs != null ? newMsgs : new Vector());

        if (newMsgs != null && !newMsgs.isEmpty()) {
            ChatMessage last = (ChatMessage) newMsgs.lastElement();
            Contact c = getContact(jid);
            if (c != null && last != null) {
                c.setStatusText(last.getText());
            }
        }
    }

    /**
     * Prepends older messages (from lazy loading) to the existing list,
     * maintaining strict chronological order and deduplicating by ID.
     * Returns the number of messages actually inserted.
     */
    public synchronized int prependOlderMessages(Jid jid, Vector olderMsgs) {
        if (jid == null || olderMsgs == null || olderMsgs.isEmpty()) return 0;
        String key = normalizeUser(jid.getUser());
        Vector list = (Vector) conversations.get(key);
        if (list == null) {
            list = new Vector();
            conversations.put(key, list);
        }

        int addedCount = 0;
        for (int i = olderMsgs.size() - 1; i >= 0; i--) {
            ChatMessage older = (ChatMessage) olderMsgs.elementAt(i);
            if (older == null) continue;
            boolean exists = false;
            String olderId = older.getId();
            for (int j = 0; j < list.size(); j++) {
                ChatMessage cur = (ChatMessage) list.elementAt(j);
                if (olderId != null && olderId.length() > 0 && olderId.equals(cur.getId())) {
                    exists = true;
                    break;
                }
                if (cur.getTimestamp() == older.getTimestamp() && cur.isFromMe() == older.isFromMe() && older.getText().equals(cur.getText())) {
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                int insertPos = 0;
                while (insertPos < list.size()) {
                    ChatMessage cur = (ChatMessage) list.elementAt(insertPos);
                    if (older.getTimestamp() <= cur.getTimestamp()) {
                        break;
                    }
                    insertPos++;
                }
                list.insertElementAt(older, insertPos);
                addedCount++;
            }
        }
        return addedCount;
    }

    /**
     * Adds a message with deduplication by message ID and content.
     * Inserts in strict chronological order by timestamp.
     */
    public synchronized boolean addMessage(ChatMessage msg) {
        if (msg == null || msg.getRemoteJid() == null) return false;
        String key = normalizeUser(msg.getRemoteJid().getUser());
        Vector list = (Vector) conversations.get(key);
        if (list == null) {
            list = new Vector();
            conversations.put(key, list);
        }

        // 1. Deduplicate by message ID
        String newId = msg.getId();
        if (newId != null && newId.length() > 0) {
            int sz = list.size();
            for (int i = sz - 1; i >= 0 && i >= sz - 30; i--) {
                ChatMessage existing = (ChatMessage) list.elementAt(i);
                if (newId.equals(existing.getId())) {
                    bumpContactToTop(msg.getRemoteJid(), msg.getText());
                    return false;
                }
            }
        }

        // 2. Deduplicate messages with same fromMe and same text within 60s
        int sz = list.size();
        for (int i = sz - 1; i >= 0 && i >= sz - 15; i--) {
            ChatMessage existing = (ChatMessage) list.elementAt(i);
            if (existing.isFromMe() == msg.isFromMe() && msg.getText() != null && msg.getText().equals(existing.getText())) {
                if (Math.abs(existing.getTimestamp() - msg.getTimestamp()) < 60000) {
                    if (newId != null && newId.length() > 0) {
                        existing.setId(newId);
                    }
                    existing.setTimestamp(msg.getTimestamp());
                    bumpContactToTop(msg.getRemoteJid(), msg.getText());
                    return false;
                }
            }
        }

        // 3. Ensure local outgoing messages are never sorted behind existing messages
        if (msg.isFromMe() && !list.isEmpty()) {
            ChatMessage last = (ChatMessage) list.lastElement();
            if (last != null && msg.getTimestamp() <= last.getTimestamp()) {
                if (msg.getId() != null && (msg.getId().startsWith("msg_") || msg.getId().startsWith("audio_"))) {
                    msg.setTimestamp(last.getTimestamp() + 1000);
                }
            }
        }

        // 4. Insert in chronological order (or append at end if timestamp >= last)
        if (list.isEmpty() || msg.getTimestamp() >= ((ChatMessage) list.lastElement()).getTimestamp()) {
            list.addElement(msg);
        } else {
            int insertIdx = list.size();
            for (int i = list.size() - 1; i >= 0; i--) {
                ChatMessage m = (ChatMessage) list.elementAt(i);
                if (msg.getTimestamp() >= m.getTimestamp()) {
                    insertIdx = i + 1;
                    break;
                }
                insertIdx = i;
            }
            list.insertElementAt(msg, insertIdx);
        }

        // 4. ALWAYS bump contact to top in chat list with latest message snippet!
        bumpContactToTop(msg.getRemoteJid(), msg.getText());

        // 5. Update unread count if incoming and not currently in active conversation
        Contact c = getContact(msg.getRemoteJid());
        if (c != null && !msg.isFromMe()) {
            if (!isConversationActive(msg.getRemoteJid())) {
                c.setUnreadCount(c.getUnreadCount() + 1);
            }
        }
        return true;
    }

    public synchronized Vector getMessages(Jid jid) {
        if (jid == null) return new Vector();
        String key = normalizeUser(jid.getUser());
        Vector list = (Vector) conversations.get(key);
        if (list == null || list.isEmpty()) {
            list = SDCardMessageStore.getInstance().loadMessages(jid);
            conversations.put(key, list);
        }
        return list;
    }

    private String normalizeUser(String u) {
        if (u == null) return "";
        int colon = u.indexOf(':');
        return colon != -1 ? u.substring(0, colon) : u;
    }
}
