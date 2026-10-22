package com.whatsapp.protocol;

import java.util.Vector;

/**
 * WhatsApp Binary XML Protocol Node.
 * Reconstructed directly from X.C0it and ProtocolJniHelper reverse-engineered
 * with REA from WhatsApp APK v2.26.41.71.
 * 
 * An XML stanza in WhatsApp binary wire protocol consists of:
 * - A tag (String)
 * - An array of attributes (KeyValue[])
 * - Either binary data (byte[]) OR an array of child nodes (ProtocolTreeNode[])
 */
public class ProtocolTreeNode {

    private String tag;
    private KeyValue[] attributes;
    private ProtocolTreeNode[] children;
    private byte[] data;

    public ProtocolTreeNode(String tag, KeyValue[] attributes) {
        this(tag, attributes, (ProtocolTreeNode[]) null, null);
    }

    public ProtocolTreeNode(String tag, KeyValue[] attributes, String textContent) {
        this(tag, attributes, (ProtocolTreeNode[]) null, textContent != null ? textContent.getBytes() : null);
    }

    public ProtocolTreeNode(String tag, KeyValue[] attributes, byte[] data) {
        this(tag, attributes, (ProtocolTreeNode[]) null, data);
    }

    public ProtocolTreeNode(String tag, KeyValue[] attributes, ProtocolTreeNode[] children) {
        this(tag, attributes, children, null);
    }

    public ProtocolTreeNode(String tag, KeyValue[] attributes, ProtocolTreeNode child) {
        this(tag, attributes, child != null ? new ProtocolTreeNode[]{child} : null, null);
    }

    public ProtocolTreeNode(String tag, KeyValue[] attributes, ProtocolTreeNode[] children, byte[] data) {
        if (tag == null) {
            throw new IllegalArgumentException("ProtocolTreeNode tag cannot be null");
        }
        this.tag = tag;
        this.attributes = attributes != null ? attributes : new KeyValue[0];
        this.children = children;
        this.data = data;
        if (children != null && data != null) {
            throw new IllegalArgumentException("ProtocolTreeNode may not have both data and children");
        }
    }

    public String getTag() {
        return tag;
    }

    public KeyValue[] getAttributes() {
        return attributes;
    }

    public ProtocolTreeNode[] getChildren() {
        return children;
    }

    public byte[] getData() {
        return data;
    }

    public String getDataAsString() {
        if (data == null) return null;
        try {
            return new String(data, "UTF-8");
        } catch (Exception e) {
            return new String(data);
        }
    }

    public String getAttribute(String key) {
        if (attributes == null || key == null) return null;
        for (int i = 0; i < attributes.length; i++) {
            if (key.equals(attributes[i].getKey())) {
                return attributes[i].getValue();
            }
        }
        return null;
    }

    public ProtocolTreeNode getChild(String tag) {
        if (children == null || tag == null) return null;
        for (int i = 0; i < children.length; i++) {
            if (tag.equals(children[i].getTag())) {
                return children[i];
            }
        }
        return null;
    }

    public ProtocolTreeNode[] getChildren(String tag) {
        if (children == null || tag == null) return new ProtocolTreeNode[0];
        Vector matches = new Vector();
        for (int i = 0; i < children.length; i++) {
            if (tag.equals(children[i].getTag())) {
                matches.addElement(children[i]);
            }
        }
        ProtocolTreeNode[] res = new ProtocolTreeNode[matches.size()];
        for (int i = 0; i < matches.size(); i++) {
            res[i] = (ProtocolTreeNode) matches.elementAt(i);
        }
        return res;
    }

    public String toXmlString() {
        StringBuffer sb = new StringBuffer();
        sb.append('<').append(tag);
        if (attributes != null) {
            for (int i = 0; i < attributes.length; i++) {
                sb.append(' ').append(attributes[i].toString());
            }
        }
        if (children == null && data == null) {
            sb.append("/>");
            return sb.toString();
        }
        sb.append('>');
        if (children != null) {
            for (int i = 0; i < children.length; i++) {
                if (children[i] != null) {
                    sb.append(children[i].toXmlString());
                }
            }
        }
        if (data != null) {
            sb.append(getDataAsString());
        }
        sb.append("</").append(tag).append('>');
        return sb.toString();
    }

    public String toString() {
        return toXmlString();
    }
}
