package com.whatsapp.protocol;

/**
 * Key-Value attribute pair for WhatsApp Binary ProtocolTreeNodes.
 * Corresponds to X.C0ir decompiled from WhatsApp APK.
 */
public class KeyValue {

    public static final byte TYPE_STRING = 0;
    public static final byte TYPE_JID = 1;

    private byte type;
    private Jid jid;
    private String key;
    private String value;

    public KeyValue(String key, String value) {
        this(null, key, value, TYPE_STRING);
    }

    public KeyValue(Jid jid, String key, String value, byte type) {
        this.jid = jid;
        this.key = key;
        this.value = value;
        this.type = type;
    }

    public String getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }

    public Jid getJid() {
        return jid;
    }

    public byte getType() {
        return type;
    }

    public String toString() {
        return key + "=\"" + (value != null ? value : "") + "\"";
    }
}
