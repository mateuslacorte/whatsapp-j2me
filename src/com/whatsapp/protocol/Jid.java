package com.whatsapp.protocol;

/**
 * Represents a WhatsApp Jabber Identifier (JID).
 * In modern WhatsApp protocol (see com.whatsapp.infra.core.jid.Jid in APK):
 * - User: <phone>@s.whatsapp.net
 * - Group: <id>@g.us
 * - Server: s.whatsapp.net
 */
public class Jid {

    public static final String DOMAIN_USER = "s.whatsapp.net";
    public static final String DOMAIN_GROUP = "g.us";
    public static final String DOMAIN_SERVER = "s.whatsapp.net";

    private String user;
    private String server;
    private String rawString;

    public Jid(String user, String server) {
        this.user = user != null ? user.trim() : "";
        this.server = server != null ? server.trim() : DOMAIN_USER;
        this.rawString = this.user.length() > 0 ? (this.user + "@" + this.server) : this.server;
    }

    public static Jid of(String raw) {
        if (raw == null || raw.length() == 0) {
            return new Jid("", DOMAIN_SERVER);
        }
        int atIdx = raw.indexOf('@');
        if (atIdx != -1) {
            String u = raw.substring(0, atIdx);
            String s = raw.substring(atIdx + 1);
            return new Jid(u, s);
        }
        return new Jid(raw, DOMAIN_USER);
    }

    public String getUser() {
        return user;
    }

    public String getServer() {
        return server;
    }

    public String getRawString() {
        return rawString;
    }

    public boolean isGroup() {
        return DOMAIN_GROUP.equals(server);
    }

    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Jid)) return false;
        Jid other = (Jid) o;
        return this.rawString.equals(other.rawString);
    }

    public int hashCode() {
        return rawString.hashCode();
    }

    public String toString() {
        return rawString;
    }
}
