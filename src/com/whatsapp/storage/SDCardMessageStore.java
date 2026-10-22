package com.whatsapp.storage;

import com.whatsapp.model.ChatMessage;
import com.whatsapp.protocol.Jid;

import javax.microedition.rms.RecordStore;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Vector;

/**
 * High-performance, lightweight message store for WhatsApp J2ME.
 * Implements in-memory fast indexing and silent background RMS persistence.
 */
public class SDCardMessageStore {

    private static SDCardMessageStore instance;
    private static final String RMS_PREFIX = "wa_c_";

    private SDCardMessageStore() {}

    public static synchronized SDCardMessageStore getInstance() {
        if (instance == null) {
            instance = new SDCardMessageStore();
        }
        return instance;
    }

    /**
     * Appends a message to persistent storage asynchronously.
     */
    public void appendMessage(final ChatMessage msg) {
        if (msg == null || msg.getRemoteJid() == null) return;

        new Thread(new Runnable() {
            public void run() {
                String rsName = getRmsName(msg.getRemoteJid());
                RecordStore rs = null;
                try {
                    rs = RecordStore.openRecordStore(rsName, true);
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    DataOutputStream dos = new DataOutputStream(baos);
                    dos.writeUTF(msg.getId() != null ? msg.getId() : "");
                    dos.writeUTF(msg.getRemoteJid().getRawString());
                    dos.writeUTF(msg.getText() != null ? msg.getText() : "");
                    dos.writeLong(msg.getTimestamp());
                    dos.writeBoolean(msg.isFromMe());
                    dos.writeUTF(msg.getQuotedId() != null ? msg.getQuotedId() : "");
                    dos.writeUTF(msg.getQuotedText() != null ? msg.getQuotedText() : "");
                    dos.writeUTF(msg.getQuotedSender() != null ? msg.getQuotedSender() : "");
                    dos.writeUTF(msg.getParticipant() != null ? msg.getParticipant() : "");
                    dos.writeUTF(msg.getSenderName() != null ? msg.getSenderName() : "");
                    dos.flush();
                    byte[] bytes = baos.toByteArray();
                    rs.addRecord(bytes, 0, bytes.length);
                } catch (Throwable t) {
                } finally {
                    if (rs != null) {
                        try { rs.closeRecordStore(); } catch (Exception ignored) {}
                    }
                }
            }
        }).start();
    }

    /**
     * Replaces and saves clean, sorted messages to persistent storage.
     */
    public void saveMessages(final Jid jid, final Vector messages) {
        if (jid == null || messages == null) return;

        new Thread(new Runnable() {
            public void run() {
                String rsName = getRmsName(jid);
                RecordStore rs = null;
                try {
                    try {
                        RecordStore.deleteRecordStore(rsName);
                    } catch (Throwable ignored) {}
                    rs = RecordStore.openRecordStore(rsName, true);
                    for (int i = 0; i < messages.size(); i++) {
                        ChatMessage msg = (ChatMessage) messages.elementAt(i);
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        DataOutputStream dos = new DataOutputStream(baos);
                        dos.writeUTF(msg.getId() != null ? msg.getId() : "");
                        dos.writeUTF(msg.getRemoteJid().getRawString());
                        dos.writeUTF(msg.getText() != null ? msg.getText() : "");
                        dos.writeLong(msg.getTimestamp());
                        dos.writeBoolean(msg.isFromMe());
                        dos.writeUTF(msg.getQuotedId() != null ? msg.getQuotedId() : "");
                        dos.writeUTF(msg.getQuotedText() != null ? msg.getQuotedText() : "");
                        dos.writeUTF(msg.getQuotedSender() != null ? msg.getQuotedSender() : "");
                        dos.writeUTF(msg.getParticipant() != null ? msg.getParticipant() : "");
                        dos.writeUTF(msg.getSenderName() != null ? msg.getSenderName() : "");
                        dos.flush();
                        byte[] bytes = baos.toByteArray();
                        rs.addRecord(bytes, 0, bytes.length);
                    }
                } catch (Throwable t) {
                } finally {
                    if (rs != null) {
                        try { rs.closeRecordStore(); } catch (Exception ignored) {}
                    }
                }
            }
        }).start();
    }

    /**
     * Loads cached messages for this contact from local persistent storage.
     */
    public synchronized Vector loadMessages(Jid jid) {
        Vector messages = new Vector();
        if (jid == null) return messages;

        String rsName = getRmsName(jid);
        RecordStore rs = null;
        try {
            rs = RecordStore.openRecordStore(rsName, false);
            if (rs != null) {
                int num = rs.getNumRecords();
                for (int i = 1; i <= num; i++) {
                    try {
                        byte[] data = rs.getRecord(i);
                        DataInputStream dis = new DataInputStream(new ByteArrayInputStream(data));
                        String id = dis.readUTF();
                        String remoteStr = dis.readUTF();
                        String text = dis.readUTF();
                        long ts = dis.readLong();
                        boolean fromMe = dis.readBoolean();
                        ChatMessage cmsg = new ChatMessage(id, Jid.of(remoteStr), text, ts, fromMe);
                        if (dis.available() > 0) {
                            String qId = dis.readUTF();
                            String qText = dis.readUTF();
                            String qSender = dis.readUTF();
                            if (qText != null && qText.length() > 0) {
                                cmsg.setQuotedId(qId);
                                cmsg.setQuotedText(qText);
                                cmsg.setQuotedSender(qSender);
                            }
                            if (dis.available() > 0) {
                                String part = dis.readUTF();
                                String sName = dis.readUTF();
                                if (part != null && part.length() > 0) cmsg.setParticipant(part);
                                if (sName != null && sName.length() > 0) cmsg.setSenderName(sName);
                            }
                        }
                        messages.addElement(cmsg);
                    } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable t) {
        } finally {
            if (rs != null) {
                try { rs.closeRecordStore(); } catch (Exception ignored) {}
            }
        }
        return messages;
    }

    private String getRmsName(Jid jid) {
        String user = jid.getUser();
        if (user.length() > 20) {
            user = user.substring(0, 20);
        }
        return (RMS_PREFIX + user).replace('+', '_').replace('@', '_');
    }
}
