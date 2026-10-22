package com.whatsapp.model;

import com.whatsapp.protocol.Jid;
import javax.microedition.lcdui.Image;

/**
 * Message model representing incoming or outgoing chat messages.
 */
public class ChatMessage {

    public static final int STATUS_PENDING = 0;
    public static final int STATUS_SENT = 1;
    public static final int STATUS_DELIVERED = 2;
    public static final int STATUS_READ = 3;

    private String id;
    private Jid remoteJid;
    private String text;
    private long timestamp;
    private boolean fromMe;
    private int status;
    private String quotedId;
    private String quotedText;
    private String quotedSender;
    private String participant;
    private String senderName;
    private boolean hasImage;
    private transient Image image;
    private transient boolean imageLoading;
    private transient boolean imageFailed;

    public ChatMessage(String id, Jid remoteJid, String text, long timestamp, boolean fromMe) {
        this.id = id;
        this.remoteJid = remoteJid;
        this.text = text;
        this.timestamp = timestamp;
        this.fromMe = fromMe;
        this.status = fromMe ? STATUS_SENT : STATUS_DELIVERED;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Jid getRemoteJid() {
        return remoteJid;
    }

    public String getText() {
        return text;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public boolean isFromMe() {
        return fromMe;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public String getQuotedId() {
        return quotedId;
    }

    public void setQuotedId(String quotedId) {
        this.quotedId = quotedId;
    }

    public String getQuotedText() {
        return quotedText;
    }

    public void setQuotedText(String quotedText) {
        this.quotedText = quotedText;
    }

    public String getQuotedSender() {
        return quotedSender;
    }

    public void setQuotedSender(String quotedSender) {
        this.quotedSender = quotedSender;
    }

    public boolean hasQuote() {
        return quotedText != null && quotedText.length() > 0;
    }

    public String getParticipant() {
        return participant;
    }

    public void setParticipant(String participant) {
        this.participant = participant;
    }

    public String getSenderName() {
        return senderName;
    }

    public void setSenderName(String senderName) {
        this.senderName = senderName;
    }

    public boolean hasImage() {
        return hasImage;
    }

    public void setHasImage(boolean hasImage) {
        this.hasImage = hasImage;
    }

    public Image getImage() {
        return image;
    }

    public void setImage(Image image) {
        this.image = image;
    }

    public boolean isImageLoading() {
        return imageLoading;
    }

    public void setImageLoading(boolean imageLoading) {
        this.imageLoading = imageLoading;
    }

    public boolean isImageFailed() {
        return imageFailed;
    }

    public void setImageFailed(boolean imageFailed) {
        this.imageFailed = imageFailed;
    }
}
