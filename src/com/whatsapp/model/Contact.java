package com.whatsapp.model;

import com.whatsapp.protocol.Jid;
import com.whatsapp.util.StringUtil;

/**
 * Contact model for chat list and address book.
 */
public class Contact {

    private Jid jid;
    private String name;
    private String statusText;
    private int unreadCount;

    public Contact(Jid jid, String name, String statusText) {
        this.jid = jid;
        String rawName = name != null ? name.trim() : "";
        if (rawName.length() == 0 || rawName.indexOf('\u2219') >= 0 || rawName.indexOf('\u00B7') >= 0) {
            rawName = jid != null ? jid.getUser() : "";
        }
        this.name = StringUtil.formatPhoneNumber(rawName);
        this.statusText = statusText != null ? statusText : "Hey there! I am using WhatsApp";
        this.unreadCount = 0;
    }

    public Jid getJid() {
        return jid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        if (name != null) {
            String rawName = name.trim();
            if (rawName.indexOf('\u2219') >= 0 || rawName.indexOf('\u00B7') >= 0) {
                rawName = jid != null ? jid.getUser() : "";
            }
            this.name = StringUtil.formatPhoneNumber(rawName);
        }
    }

    public String getStatusText() {
        return statusText;
    }

    public void setStatusText(String statusText) {
        this.statusText = statusText;
    }

    public int getUnreadCount() {
        return unreadCount;
    }

    public void setUnreadCount(int unreadCount) {
        this.unreadCount = unreadCount;
    }

    public String toString() {
        return name + (unreadCount > 0 ? " (" + unreadCount + ")" : "");
    }
}
