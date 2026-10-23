package com.whatsapp.ui;

import com.whatsapp.i18n.Text;

import com.whatsapp.model.ChatMessage;
import com.whatsapp.model.Contact;
import com.whatsapp.net.BaileysBridgeClient;
import com.whatsapp.storage.ChatStore;
import com.whatsapp.storage.SDCardMessageStore;
import com.whatsapp.util.ImageUtil;
import com.whatsapp.util.StringUtil;
import com.whatsapp.voice.VoiceAudioEngine;

import javax.microedition.lcdui.*;
import java.util.Vector;

/**
 * WhatsApp J2ME Conversation Screen.
 * Supports fast initial load, lazy loading on scroll, message selection,
 * and quoting / replying to selected messages.
 */
public class ConversationCanvas extends Canvas implements CommandListener, VoiceAudioEngine.RecordListener {

    private ScreenController controller;
    private Contact contact;
    private int headerHeight = Theme.getHeaderHeight(true);
    private int inputHeight = 36;
    private int replyBannerHeight = Math.max(34, Theme.FONT_CHAT_TITLE.getHeight() + Theme.FONT_TIMESTAMP.getHeight() + 6);
    private static final int OLDER_LOADING_INDICATOR_H = 38;
    private int scrollOffset = 99999;
    private int totalContentHeight = 0;
    private boolean isRecordingAudio = false;
    private long screenOpenTime = System.currentTimeMillis();

    private boolean isLoadingInitial = false;
    private boolean isLoadingOlder = false;
    private boolean hasMoreOlder = true;

    private int selectedMessageIndex = -1;
    private ChatMessage replyingToMessage = null;

    // Cache of rendered bubble bounds: int[]{ x, y, w, h, index }
    private Vector renderedBubbleBounds = new Vector();

    private Command sendCmd;
    private Command selectCmd;
    private Command voiceCmd;
    private Command backCmd;
    private Command replyCmd;
    private Command cancelSelectCmd;

    public ConversationCanvas(ScreenController controller, Contact contact) {
        this.controller = controller;
        this.contact = contact;
        if (contact != null) {
            contact.setUnreadCount(0);
            ChatStore.getInstance().setActiveConversation(contact.getJid());
            ChatStore.getInstance().clearUnread(contact.getJid());
        }

        sendCmd = new Command(Text.get("Digitar Msg"), Command.OK, 1);
        selectCmd = new Command(Text.get("Selecionar Msg"), Command.ITEM, 2);
        voiceCmd = new Command(Text.get("Audio PTT"), Command.ITEM, 3);
        backCmd = new Command(Text.get("Voltar"), Command.BACK, 4);

        replyCmd = new Command(Text.get("Responder"), Command.OK, 1);
        cancelSelectCmd = new Command(Text.get("Desmarcar"), Command.CANCEL, 2);

        updateCommandList();
        setCommandListener(this);

        // Fast initial load of latest 15 messages
        loadInitialMessages();
    }

    private void updateCommandList() {
        removeCommand(sendCmd);
        removeCommand(selectCmd);
        removeCommand(voiceCmd);
        removeCommand(backCmd);
        removeCommand(replyCmd);
        removeCommand(cancelSelectCmd);

        if (selectedMessageIndex != -1) {
            addCommand(replyCmd);
            addCommand(cancelSelectCmd);
            addCommand(backCmd);
        } else {
            addCommand(sendCmd);
            addCommand(selectCmd);
            addCommand(voiceCmd);
            addCommand(backCmd);
        }
    }

    public void refresh() {
        Contact c = ChatStore.getInstance().getContact(contact.getJid());
        if (c != null && c.getName() != null && c.getName().trim().length() > 0 && !c.getName().equals(c.getJid().getUser())) {
            this.contact = c;
        }
        repaint();
    }

    private void loadInitialMessages() {
        if (isLoadingInitial) return;
        isLoadingInitial = true;
        new Thread(new Runnable() {
            public void run() {
                try {
                    BaileysBridgeClient.getInstance().markChatRead(contact.getJid().getRawString());
                    Vector initial = BaileysBridgeClient.getInstance().fetchMessages(contact.getJid().getRawString(), 15, 0);
                    if (initial != null) {
                        if (initial.size() < 15) {
                            hasMoreOlder = false;
                        }
                        scrollOffset = 99999;
                    }
                } catch (Exception ignored) {
                } finally {
                    isLoadingInitial = false;
                    repaint();
                }
            }
        }).start();
    }

    private synchronized void loadOlderMessagesIfNeeded() {
        if (isLoadingOlder || !hasMoreOlder) return;
        Vector msgs = ChatStore.getInstance().getMessages(contact.getJid());
        if (msgs == null || msgs.isEmpty()) return;

        ChatMessage oldest = (ChatMessage) msgs.firstElement();
        if (oldest == null) return;
        final long oldestTs = oldest.getTimestamp();

        isLoadingOlder = true;
        repaint();

        new Thread(new Runnable() {
            public void run() {
                try {
                    Vector older = BaileysBridgeClient.getInstance().fetchMessages(contact.getJid().getRawString(), 15, oldestTs);
                    if (older == null || older.isEmpty()) {
                        hasMoreOlder = false;
                    } else {
                        if (older.size() < 15) {
                            hasMoreOlder = false;
                        }
                        int addedH = measureBatchHeight(older, getWidth() - 50);
                        scrollOffset += addedH;
                        if (selectedMessageIndex != -1) {
                            selectedMessageIndex += older.size();
                        }
                    }
                } catch (Exception ignored) {
                } finally {
                    isLoadingOlder = false;
                    repaint();
                }
            }
        }).start();
    }

    private static final int[] SENDER_COLORS = {
        0x008069, // Teal
        0xD35400, // Rust orange
        0x1E88E5, // Blue
        0x8E44AD, // Purple
        0x27AE60, // Green
        0xC0392B, // Red
        0xD81B60, // Pink
        0x00A884  // Emerald
    };

    private boolean isGroupChat() {
        return contact != null && contact.getJid() != null && contact.getJid().getRawString().indexOf("@g.us") >= 0;
    }

    private String getSenderTitle(ChatMessage msg) {
        if (msg == null || msg.isFromMe() || !isGroupChat()) return null;
        String sName = msg.getSenderName();
        if (sName != null && sName.trim().length() > 0) {
            String title = sName.trim();
            if (!title.startsWith("~") && !StringUtil.isNumericOrPhone(title)) {
                title = "~ " + title;
            }
            return title;
        }
        String part = msg.getParticipant();
        if (part != null && part.trim().length() > 0) {
            if (part.indexOf("@lid") >= 0) {
                return Text.get("~ Participante");
            }
            return StringUtil.formatPhoneNumber(part);
        }
        return null;
    }

    private String getSenderPhone(ChatMessage msg, String title) {
        if (msg == null || msg.isFromMe() || !isGroupChat()) return null;
        String part = msg.getParticipant();
        if (part != null && part.trim().length() > 0) {
            if (part.indexOf("@lid") >= 0) {
                return null;
            }
            String phone = StringUtil.formatPhoneNumber(part);
            if (title == null || title.equals(phone) || title.indexOf(part) >= 0) {
                return null;
            }
            return phone;
        }
        return null;
    }

    private int getSenderHeaderHeight(ChatMessage msg) {
        if (getSenderTitle(msg) != null) {
            return Theme.FONT_CHAT_TITLE.getHeight() + 4;
        }
        return 0;
    }

    private int getQuoteCardHeight(ChatMessage msg) {
        if (msg != null && msg.hasQuote()) {
            return 4 + Theme.FONT_CHAT_TITLE.getHeight() + 2 + Theme.FONT_TIMESTAMP.getHeight() + 4;
        }
        return 0;
    }

    private int getQuoteCardGap(ChatMessage msg) {
        if (msg != null && msg.hasQuote()) {
            return 6;
        }
        return 0;
    }

    private int getImageBlockHeight(ChatMessage msg, int maxW) {
        if (msg == null || !msg.hasImage()) return 0;
        Image img = msg.getImage();
        if (img != null) {
            return img.getHeight() + 6;
        }
        return 86; // 80px placeholder + 6px bottom gap
    }

    private int getImageBlockWidth(ChatMessage msg, int maxW) {
        if (msg == null || !msg.hasImage()) return 0;
        Image img = msg.getImage();
        if (img != null) {
            return img.getWidth();
        }
        return Math.min(140, maxW);
    }

    private int getTimeWidth(ChatMessage msg) {
        if (msg == null) return 0;
        String timeStr = StringUtil.formatMessageTime(msg.getTimestamp());
        if (timeStr == null || timeStr.length() == 0) return 0;
        int w = Theme.FONT_TIMESTAMP.stringWidth(timeStr);
        if (msg.isFromMe()) {
            w += 3 + 13; // 3px gap + 13px ticks
        }
        return w;
    }

    private int getContentWidth(ChatMessage msg, int longestLineWidth, int maxContentW) {
        String senderTitle = getSenderTitle(msg);
        String senderPhone = getSenderPhone(msg, senderTitle);
        int senderW = 0;
        if (senderTitle != null) {
            senderW = Theme.FONT_CHAT_TITLE.stringWidth(senderTitle);
            if (senderPhone != null) {
                senderW += Theme.FONT_TIMESTAMP.stringWidth("  " + senderPhone);
            }
            if (senderW > maxContentW) senderW = maxContentW;
        }

        int quoteW = 0;
        if (msg != null && msg.hasQuote()) {
            String qAuthorName = msg.getQuotedSender() != null ? StringUtil.formatPhoneNumber(msg.getQuotedSender()) : "Quote";
            int qAuthorW = Theme.FONT_CHAT_TITLE.stringWidth(qAuthorName);
            String qSnippet = msg.getQuotedText();
            if (qSnippet == null) qSnippet = "";
            int qTextW = Theme.FONT_TIMESTAMP.stringWidth(qSnippet);
            quoteW = Math.max(qAuthorW, qTextW) + 24;
            if (quoteW > maxContentW) quoteW = maxContentW;
        }

        int imageW = getImageBlockWidth(msg, maxContentW);
        return Math.max(longestLineWidth, Math.max(quoteW, Math.max(senderW, imageW)));
    }

    private boolean doesTimeNeedExtraLine(int textLinesCount, int lastLineW, int timeW, int contentW, int maxContentW) {
        if (timeW <= 0) return false;
        if (textLinesCount == 0) return true;
        if (textLinesCount == 1) {
            return (lastLineW + 8 + timeW > maxContentW);
        } else {
            if (lastLineW + 8 + timeW <= contentW) {
                return false;
            } else if (lastLineW + 8 + timeW <= maxContentW) {
                return false;
            } else {
                return true;
            }
        }
    }

    private int calculateBubbleHeight(ChatMessage msg, Font font, int maxBubbleW) {
        if (msg == null) return 0;
        int fontH = font.getHeight();
        String rawText = msg.getText();
        boolean showText = true;
        if (msg.hasImage() && (rawText == null || rawText.length() == 0 || "[Imagem]".equals(rawText))) {
            showText = false;
        }

        int textLinesCount = 0;
        int longestLineWidth = 0;
        int lastLineW = 0;
        if (showText) {
            if (rawText == null || rawText.length() == 0) {
                rawText = Text.get("[Mensagem]");
            }
            Vector lines = wrapText(rawText, font, maxBubbleW - 20);
            textLinesCount = lines.size();
            for (int l = 0; l < textLinesCount; l++) {
                int lw = font.stringWidth((String) lines.elementAt(l));
                if (lw > longestLineWidth) longestLineWidth = lw;
            }
            if (textLinesCount > 0) {
                lastLineW = font.stringWidth((String) lines.elementAt(textLinesCount - 1));
            }
        }

        int timeW = getTimeWidth(msg);
        int contentW = getContentWidth(msg, longestLineWidth, maxBubbleW - 20);
        boolean timeOnExtraLine = doesTimeNeedExtraLine(textLinesCount, lastLineW, timeW, contentW, maxBubbleW - 20);

        return calculateBubbleHeight(msg, textLinesCount, fontH, maxBubbleW - 20, timeOnExtraLine);
    }

    private int calculateBubbleHeight(ChatMessage msg, int textLinesCount, int fontH, int maxW) {
        return calculateBubbleHeight(msg, textLinesCount, fontH, maxW, false);
    }

    private int calculateBubbleHeight(ChatMessage msg, int textLinesCount, int fontH, int maxW, boolean timeOnExtraLine) {
        int senderH = getSenderHeaderHeight(msg);
        int quoteH = getQuoteCardHeight(msg);
        int quoteGap = getQuoteCardGap(msg);
        int imageH = getImageBlockHeight(msg, maxW);
        int textH = (textLinesCount > 0) ? (textLinesCount * (fontH + 2)) : 0;
        int timeH = timeOnExtraLine ? (Theme.FONT_TIMESTAMP.getHeight() + 2) : 0;
        return 6 + senderH + quoteH + quoteGap + imageH + textH + timeH + 6;
    }

    private void drawStatusTicks(Graphics g, int x, int y, int status) {
        if (status == ChatMessage.STATUS_READ) {
            g.setColor(0x34B7F1); // WhatsApp signature read blue
        } else {
            g.setColor(0x667766); // Muted green-gray
        }

        // First tick
        g.drawLine(x, y + 4, x + 3, y + 7);
        g.drawLine(x + 3, y + 7, x + 8, y + 2);

        // Second tick if delivered or read
        if (status >= ChatMessage.STATUS_DELIVERED) {
            g.drawLine(x + 4, y + 4, x + 7, y + 7);
            g.drawLine(x + 7, y + 7, x + 12, y + 2);
        }
    }

    private void requestImageLoad(final ChatMessage msg, final int maxW) {
        if (msg == null || !msg.hasImage() || msg.getImage() != null || msg.isImageLoading() || msg.isImageFailed()) {
            return;
        }
        msg.setImageLoading(true);
        new Thread(new Runnable() {
            public void run() {
                try {
                    Image rawImg = BaileysBridgeClient.getInstance().fetchImage(msg.getId(), maxW);
                    if (rawImg != null) {
                        Image scaled = ImageUtil.scaleImageToFit(rawImg, maxW, 220);
                        msg.setImage(scaled);
                    } else {
                        msg.setImageFailed(true);
                    }
                } catch (Throwable t) {
                    msg.setImageFailed(true);
                } finally {
                    msg.setImageLoading(false);
                    repaint();
                }
            }
        }).start();
    }

    private int measureBatchHeight(Vector batch, int maxBubbleW) {
        if (batch == null || batch.isEmpty()) return 0;
        Font font = Theme.FONT_CHAT_MSG;
        int totalH = 0;
        for (int i = 0; i < batch.size(); i++) {
            ChatMessage msg = (ChatMessage) batch.elementAt(i);
            int bubbleH = calculateBubbleHeight(msg, font, maxBubbleW);
            totalH += bubbleH + 6;
        }
        return totalH;
    }

    protected void paint(Graphics g) {
        int width = getWidth();
        int height = getHeight();

        boolean showReplyBanner = (replyingToMessage != null);
        int currentReplyH = showReplyBanner ? replyBannerHeight : 0;

        int chatAreaY = headerHeight;
        int chatAreaHeight = height - headerHeight - inputHeight - currentReplyH;

        // 1. Wallpaper background
        g.setColor(Theme.COLOR_BG_WALLPAPER);
        g.fillRect(0, 0, width, height);

        // 2. Render Messages with Clipping
        int origClipX = g.getClipX();
        int origClipY = g.getClipY();
        int origClipW = g.getClipWidth();
        int origClipH = g.getClipHeight();

        g.setClip(0, chatAreaY, width, chatAreaHeight);

        Vector msgs = ChatStore.getInstance().getMessages(contact.getJid());
        Font font = Theme.FONT_CHAT_MSG;
        int fontH = font.getHeight();
        int maxBubbleW = width - 50;

        int measuredContentH = 10;
        if (isLoadingOlder) {
            measuredContentH += OLDER_LOADING_INDICATOR_H;
        }
        for (int i = 0; i < msgs.size(); i++) {
            ChatMessage msg = (ChatMessage) msgs.elementAt(i);
            int bubbleH = calculateBubbleHeight(msg, font, maxBubbleW);
            measuredContentH += bubbleH + 6;
        }
        totalContentHeight = measuredContentH;

        int maxScroll = Math.max(0, totalContentHeight - chatAreaHeight + 10);
        if (scrollOffset > maxScroll) {
            scrollOffset = maxScroll;
        }
        if (scrollOffset < 0) {
            scrollOffset = 0;
        }

        // Auto trigger lazy loading if scrolled near top
        if (scrollOffset <= 20 && hasMoreOlder && !isLoadingOlder && !msgs.isEmpty()) {
            loadOlderMessagesIfNeeded();
        }

        int y = chatAreaY + 10 - scrollOffset;

        // Top loading indicator
        if (isLoadingOlder) {
            if (y >= chatAreaY - 30 && y <= chatAreaY + chatAreaHeight) {
                int pillH = Theme.FONT_TIMESTAMP.getHeight() + 8;
                int textW = Theme.FONT_TIMESTAMP.stringWidth(Text.get("Carregando anteriores..."));
                int pillW = Math.max(180, textW + 24);
                int pillX = (width - pillW) / 2;
                g.setColor(0xDDDDDD);
                g.fillRoundRect(pillX, y, pillW, pillH, pillH, pillH);
                g.setColor(0x555555);
                g.setFont(Theme.FONT_TIMESTAMP);
                int textY = y + (pillH - Theme.FONT_TIMESTAMP.getHeight()) / 2;
                g.drawString(Text.get("Carregando anteriores..."), width / 2, textY, Graphics.TOP | Graphics.HCENTER);
            }
            y += OLDER_LOADING_INDICATOR_H;
        }

        renderedBubbleBounds.removeAllElements();

        if (msgs.isEmpty()) {
            g.setColor(Theme.COLOR_TEXT_MUTED);
            g.setFont(Theme.FONT_CHAT_MSG);
            if (isLoadingInitial) {
                g.drawString(Text.get("Carregando mensagens..."), width / 2, chatAreaY + (chatAreaHeight / 2), Graphics.TOP | Graphics.HCENTER);
            } else {
                g.drawString(Text.get("Nenhuma mensagem nesta conversa"), width / 2, chatAreaY + (chatAreaHeight / 2) - 10, Graphics.TOP | Graphics.HCENTER);
                g.drawString(Text.get("Pressione OK para digitar"), width / 2, chatAreaY + (chatAreaHeight / 2) + 10, Graphics.TOP | Graphics.HCENTER);
            }
        } else {
            g.setFont(font);

            for (int i = 0; i < msgs.size(); i++) {
                ChatMessage msg = (ChatMessage) msgs.elementAt(i);
                boolean isMe = msg.isFromMe();
                String rawText = msg.getText();
                boolean showText = true;
                if (msg.hasImage() && (rawText == null || rawText.length() == 0 || "[Imagem]".equals(rawText))) {
                    showText = false;
                }

                Vector lines;
                int textLinesCount = 0;
                int longestLineWidth = 0;
                int lastLineW = 0;
                if (showText) {
                    if (rawText == null || rawText.length() == 0) {
                        rawText = Text.get("[Mensagem]");
                    }
                    lines = wrapText(rawText, font, maxBubbleW - 20);
                    textLinesCount = lines.size();
                    for (int l = 0; l < textLinesCount; l++) {
                        int lw = font.stringWidth((String) lines.elementAt(l));
                        if (lw > longestLineWidth) longestLineWidth = lw;
                    }
                    if (textLinesCount > 0) {
                        lastLineW = font.stringWidth((String) lines.elementAt(textLinesCount - 1));
                    }
                } else {
                    lines = new Vector();
                }

                String senderTitle = getSenderTitle(msg);
                String senderPhone = getSenderPhone(msg, senderTitle);
                boolean hasQuote = msg.hasQuote();

                int timeW = getTimeWidth(msg);
                int contentW = getContentWidth(msg, longestLineWidth, maxBubbleW - 20);
                boolean timeOnExtraLine = doesTimeNeedExtraLine(textLinesCount, lastLineW, timeW, contentW, maxBubbleW - 20);

                if (!timeOnExtraLine && textLinesCount > 0) {
                    if (lastLineW + 8 + timeW > contentW && lastLineW + 8 + timeW <= maxBubbleW - 20) {
                        contentW = lastLineW + 8 + timeW;
                    }
                }
                if (timeW > 0) {
                    contentW = Math.max(contentW, timeW);
                }
                int bubbleW = Math.max(contentW + 20, 70);
                if (bubbleW > maxBubbleW) bubbleW = maxBubbleW;
                int bubbleH = calculateBubbleHeight(msg, textLinesCount, fontH, maxBubbleW - 20, timeOnExtraLine);

                int bubbleX = isMe ? (width - bubbleW - 10) : 10;

                renderedBubbleBounds.addElement(new int[]{bubbleX, y, bubbleW, bubbleH, i});

                if (y + bubbleH >= chatAreaY && y <= chatAreaY + chatAreaHeight) {
                    // Selected message highlight outline
                    if (i == selectedMessageIndex) {
                        g.setColor(Theme.COLOR_ACCENT_GREEN);
                        g.drawRoundRect(bubbleX - 2, y - 2, bubbleW + 4, bubbleH + 4, 10, 10);
                        g.drawRoundRect(bubbleX - 1, y - 1, bubbleW + 2, bubbleH + 2, 9, 9);
                    }

                    // Bubble body
                    g.setColor(isMe ? Theme.COLOR_BUBBLE_OUT : Theme.COLOR_BUBBLE_IN);
                    g.fillRoundRect(bubbleX, y, bubbleW, bubbleH, 8, 8);

                    g.setColor(isMe ? 0xC8E8B2 : 0xDDDDDD);
                    g.drawRoundRect(bubbleX, y, bubbleW, bubbleH, 8, 8);

                    int curY = y + 6;

                    // Group participant header (e.g. "~ OrDeP   +55 98 99172-7366")
                    if (senderTitle != null) {
                        int colorIdx = Math.abs(senderTitle.hashCode()) % SENDER_COLORS.length;
                        g.setFont(Theme.FONT_CHAT_TITLE);
                        g.setColor(SENDER_COLORS[colorIdx]);

                        int availHeaderW = bubbleW - 20;
                        if (senderPhone != null) {
                            int phoneW = Theme.FONT_TIMESTAMP.stringWidth(senderPhone);
                            int spaceW = Theme.FONT_CHAT_TITLE.stringWidth("  ");
                            int maxTitleW = availHeaderW - phoneW - spaceW;

                            if (maxTitleW > 25) {
                                String drawTitle = senderTitle;
                                if (Theme.FONT_CHAT_TITLE.stringWidth(drawTitle) > maxTitleW) {
                                    while (drawTitle.length() > 3 && Theme.FONT_CHAT_TITLE.stringWidth(drawTitle + "...") > maxTitleW) {
                                        drawTitle = drawTitle.substring(0, drawTitle.length() - 1);
                                    }
                                    drawTitle += "...";
                                }
                                g.drawString(drawTitle, bubbleX + 10, curY, Graphics.TOP | Graphics.LEFT);

                                int titleW = Theme.FONT_CHAT_TITLE.stringWidth(drawTitle + "  ");
                                g.setFont(Theme.FONT_TIMESTAMP);
                                g.setColor(Theme.COLOR_TEXT_MUTED);
                                g.drawString(senderPhone, bubbleX + 10 + titleW, curY + 1, Graphics.TOP | Graphics.LEFT);
                            } else {
                                String solo = (senderTitle.indexOf(Text.get("Participante")) >= 0) ? senderPhone : senderTitle;
                                if (Theme.FONT_CHAT_TITLE.stringWidth(solo) > availHeaderW) {
                                    while (solo.length() > 3 && Theme.FONT_CHAT_TITLE.stringWidth(solo + "...") > availHeaderW) {
                                        solo = solo.substring(0, solo.length() - 1);
                                    }
                                    solo += "...";
                                }
                                g.drawString(solo, bubbleX + 10, curY, Graphics.TOP | Graphics.LEFT);
                            }
                        } else {
                            String drawTitle = senderTitle;
                            if (Theme.FONT_CHAT_TITLE.stringWidth(drawTitle) > availHeaderW) {
                                while (drawTitle.length() > 3 && Theme.FONT_CHAT_TITLE.stringWidth(drawTitle + "...") > availHeaderW) {
                                    drawTitle = drawTitle.substring(0, drawTitle.length() - 1);
                                }
                                drawTitle += "...";
                            }
                            g.drawString(drawTitle, bubbleX + 10, curY, Graphics.TOP | Graphics.LEFT);
                        }

                        curY += getSenderHeaderHeight(msg);
                    }

                    // If message contains a quote, render quote preview card
                    if (hasQuote) {
                        int qCardX = bubbleX + 8;
                        int qCardY = curY;
                        int qCardW = bubbleW - 16;
                        int qCardH = getQuoteCardHeight(msg);

                        g.setColor(isMe ? 0xD0EEBA : 0xF0F0F0);
                        g.fillRoundRect(qCardX, qCardY, qCardW, qCardH, 6, 6);

                        // Left teal/green accent vertical bar
                        g.setColor(Theme.COLOR_PRIMARY_TEAL);
                        g.fillRoundRect(qCardX, qCardY, 4, qCardH, 2, 2);

                        int maxQTextW = qCardW - 14;

                        // Quoted author
                        g.setFont(Theme.FONT_CHAT_TITLE);
                        g.setColor(Theme.COLOR_PRIMARY_TEAL);
                        String qSender = msg.getQuotedSender() != null ? StringUtil.formatPhoneNumber(msg.getQuotedSender()) : Text.get("Mensagem");
                        if (Theme.FONT_CHAT_TITLE.stringWidth(qSender) > maxQTextW) {
                            while (qSender.length() > 3 && Theme.FONT_CHAT_TITLE.stringWidth(qSender + "...") > maxQTextW) {
                                qSender = qSender.substring(0, qSender.length() - 1);
                            }
                            qSender += "...";
                        }
                        int qAuthorY = qCardY + 4;
                        g.drawString(qSender, qCardX + 8, qAuthorY, Graphics.TOP | Graphics.LEFT);

                        // Quoted text snippet
                        g.setFont(Theme.FONT_TIMESTAMP);
                        g.setColor(0x555555);
                        String qSnippet = msg.getQuotedText();
                        if (qSnippet == null) qSnippet = "";
                        if (qSnippet.indexOf('\n') >= 0) {
                            qSnippet = qSnippet.replace('\n', ' ');
                        }
                        if (Theme.FONT_TIMESTAMP.stringWidth(qSnippet) > maxQTextW) {
                            while (qSnippet.length() > 3 && Theme.FONT_TIMESTAMP.stringWidth(qSnippet + "...") > maxQTextW) {
                                qSnippet = qSnippet.substring(0, qSnippet.length() - 1);
                            }
                            qSnippet += "...";
                        }
                        int qTextY = qAuthorY + Theme.FONT_CHAT_TITLE.getHeight() + 2;
                        g.drawString(qSnippet, qCardX + 8, qTextY, Graphics.TOP | Graphics.LEFT);

                        curY += qCardH + getQuoteCardGap(msg);
                    }

                    // Image block (if message has image)
                    if (msg.hasImage()) {
                        int maxImageW = bubbleW - 20;
                        int imgX = bubbleX + 10;
                        int imgY = curY;

                        Image img = msg.getImage();
                        if (img != null) {
                            g.drawImage(img, imgX, imgY, Graphics.TOP | Graphics.LEFT);
                            curY += img.getHeight() + 6;
                        } else {
                            int boxW = getImageBlockWidth(msg, maxImageW);
                            int boxH = 80;
                            g.setColor(isMe ? 0xD2EDB5 : 0xEFEFEF);
                            g.fillRoundRect(imgX, imgY, boxW, boxH, 6, 6);
                            g.setColor(isMe ? 0xB2CF95 : 0xD5D5D5);
                            g.drawRoundRect(imgX, imgY, boxW, boxH, 6, 6);

                            g.setFont(Theme.FONT_TIMESTAMP);
                            if (msg.isImageFailed()) {
                                g.setColor(Theme.COLOR_TEXT_MUTED);
                                g.drawString(Text.get("Imagem indisponível"), imgX + boxW / 2, imgY + (boxH - Theme.FONT_TIMESTAMP.getHeight()) / 2, Graphics.TOP | Graphics.HCENTER);
                            } else {
                                g.setColor(Theme.COLOR_PRIMARY_TEAL);
                                g.drawString(Text.get("Carregando imagem..."), imgX + boxW / 2, imgY + (boxH - Theme.FONT_TIMESTAMP.getHeight()) / 2, Graphics.TOP | Graphics.HCENTER);
                                requestImageLoad(msg, maxBubbleW - 20);
                            }
                            curY += boxH + 6;
                        }
                    }

                    // Message text lines
                    if (textLinesCount > 0) {
                        g.setFont(font);
                        g.setColor(Theme.COLOR_TEXT_MAIN);
                        for (int l = 0; l < textLinesCount; l++) {
                            String lineStr = (String) lines.elementAt(l);
                            g.drawString(lineStr, bubbleX + 10, curY + (l * (fontH + 2)), Graphics.TOP | Graphics.LEFT);
                        }
                    }

                    // Message timestamp & status ticks
                    if (timeW > 0) {
                        String timeStr = StringUtil.formatMessageTime(msg.getTimestamp());
                        int timeY = y + bubbleH - Theme.FONT_TIMESTAMP.getHeight() - 5;
                        int timeX = bubbleX + bubbleW - 10 - timeW;

                        g.setFont(Theme.FONT_TIMESTAMP);
                        g.setColor(isMe ? 0x667766 : Theme.COLOR_TEXT_MUTED);
                        g.drawString(timeStr, timeX, timeY, Graphics.TOP | Graphics.LEFT);

                        if (isMe) {
                            int ticksX = timeX + Theme.FONT_TIMESTAMP.stringWidth(timeStr) + 3;
                            int tickY = timeY + (Theme.FONT_TIMESTAMP.getHeight() - 8);
                            drawStatusTicks(g, ticksX, tickY, msg.getStatus());
                        }
                    }
                }

                y += bubbleH + 6;
            }
        }

        // Restore clipping before drawing chrome
        g.setClip(origClipX, origClipY, origClipW, origClipH);

        // 3. WhatsApp Reply Banner (if active)
        if (showReplyBanner && replyingToMessage != null) {
            int bannerY = height - inputHeight - replyBannerHeight;
            g.setColor(0xEEEEEE);
            g.fillRect(0, bannerY, width, replyBannerHeight);
            g.setColor(Theme.COLOR_DIVIDER);
            g.drawLine(0, bannerY, width, bannerY);

            // Green left accent bar
            g.setColor(Theme.COLOR_ACCENT_GREEN);
            g.fillRect(10, bannerY + 3, 3, replyBannerHeight - 6);

            int titleH = Theme.FONT_CHAT_TITLE.getHeight();
            // Quoted author
            g.setFont(Theme.FONT_CHAT_TITLE);
            g.setColor(Theme.COLOR_PRIMARY_TEAL);
            String replyAuthor = replyingToMessage.isFromMe() ? Text.get("Você") :
                (replyingToMessage.getSenderName() != null && replyingToMessage.getSenderName().length() > 0 ? replyingToMessage.getSenderName() :
                (replyingToMessage.getParticipant() != null ? StringUtil.formatPhoneNumber(replyingToMessage.getParticipant()) : contact.getName()));
            replyAuthor = StringUtil.formatPhoneNumber(replyAuthor);
            int maxBannerTextW = width - 50;
            if (replyAuthor != null) {
                String repTitle = Text.get("Respondendo a ") + replyAuthor;
                if (Theme.FONT_CHAT_TITLE.stringWidth(repTitle) > maxBannerTextW) {
                    while (repTitle.length() > 3 && Theme.FONT_CHAT_TITLE.stringWidth(repTitle + "...") > maxBannerTextW) {
                        repTitle = repTitle.substring(0, repTitle.length() - 1);
                    }
                    repTitle += "...";
                }
                g.drawString(repTitle, 18, bannerY + 3, Graphics.TOP | Graphics.LEFT);
            }

            // Quoted preview snippet
            g.setFont(Theme.FONT_TIMESTAMP);
            g.setColor(0x666666);
            String replySnippet = replyingToMessage.getText();
            if (replySnippet != null) {
                if (replySnippet.indexOf('\n') >= 0) {
                    replySnippet = replySnippet.replace('\n', ' ');
                }
                if (Theme.FONT_TIMESTAMP.stringWidth(replySnippet) > maxBannerTextW) {
                    while (replySnippet.length() > 3 && Theme.FONT_TIMESTAMP.stringWidth(replySnippet + "...") > maxBannerTextW) {
                        replySnippet = replySnippet.substring(0, replySnippet.length() - 1);
                    }
                    replySnippet += "...";
                }
                g.drawString(replySnippet, 18, bannerY + 3 + titleH + 1, Graphics.TOP | Graphics.LEFT);
            }

            // Close button [X]
            g.setFont(Theme.FONT_CHAT_TITLE);
            g.setColor(0x888888);
            g.drawString("[X]", width - 12, bannerY + (replyBannerHeight - titleH) / 2, Graphics.TOP | Graphics.RIGHT);
        }

        // 4. Bottom WhatsApp Input Bar
        int inputY = height - inputHeight;
        g.setColor(0xF0F0F0);
        g.fillRect(0, inputY, width, inputHeight);
        g.setColor(Theme.COLOR_DIVIDER);
        g.drawLine(0, inputY, width, inputY);

        // Input pill
        g.setColor(Theme.COLOR_WHITE);
        g.fillRoundRect(10, inputY + 4, width - 20, inputHeight - 8, 15, 15);
        g.setColor(Theme.COLOR_DIVIDER);
        g.drawRoundRect(10, inputY + 4, width - 20, inputHeight - 8, 15, 15);

        g.setColor(isRecordingAudio ? 0xCC0000 : Theme.COLOR_TEXT_MUTED);
        g.setFont(Theme.FONT_CHAT_MSG);
        if (isRecordingAudio) {
            g.drawString(Text.get("Gravando PTT... OK para enviar"), 22, inputY + 9, Graphics.TOP | Graphics.LEFT);
        } else if (selectedMessageIndex != -1) {
            g.drawString(Text.get("Msg selecionada (OK: Responder)"), 22, inputY + 9, Graphics.TOP | Graphics.LEFT);
        } else if (showReplyBanner) {
            g.drawString(Text.get("Pressione OK para responder..."), 22, inputY + 9, Graphics.TOP | Graphics.LEFT);
        } else {
            g.drawString(Text.get("Pressione OK para digitar..."), 22, inputY + 9, Graphics.TOP | Graphics.LEFT);
        }

        // 5. WhatsApp Dark Teal Header
        String displayName = contact.getName();
        if (displayName == null || displayName.trim().length() == 0
            || displayName.indexOf('\u2219') >= 0 || displayName.indexOf('\u00B7') >= 0) {
            displayName = contact.getJid().getUser();
        }
        displayName = StringUtil.formatPhoneNumber(displayName);
        String sub = getHeaderSubtitle();
        Theme.drawWhatsAppHeader(g, width, headerHeight, displayName, sub);
    }

    private String getHeaderSubtitle() {
        if (isRecordingAudio) {
            return Text.get("Gravando audio...");
        }
        if (selectedMessageIndex != -1) {
            return Text.get("1 selecionada");
        }
        if (isGroupChat()) {
            return Text.get("toque para dados do grupo");
        } else {
            String phone = contact != null && contact.getJid() != null ? contact.getJid().getUser() : null;
            if (phone != null && phone.length() > 0) {
                String formatted = StringUtil.formatPhoneNumber(phone);
                String name = contact.getName();
                if (name != null && !name.equals(phone) && !name.equals(formatted)) {
                    return formatted;
                }
            }
            return "online";
        }
    }

    private Vector wrapText(String text, Font font, int maxWidth) {
        Vector lines = new Vector();
        if (text == null) return lines;
        int len = text.length();
        int start = 0;

        while (start < len) {
            int end = start;
            int lastSpace = -1;
            while (end < len) {
                char c = text.charAt(end);
                if (c == '\n') {
                    break;
                }
                if (c == ' ') {
                    lastSpace = end;
                }
                int curWidth = font.substringWidth(text, start, (end + 1) - start);
                if (curWidth > maxWidth) {
                    if (lastSpace > start) {
                        end = lastSpace;
                    }
                    break;
                }
                end++;
            }
            if (end == start) end = start + 1;
            String line = text.substring(start, end);
            lines.addElement(line.trim());
            start = end;
            if (start < len && (text.charAt(start) == ' ' || text.charAt(start) == '\n')) {
                start++;
            }
        }
        if (lines.isEmpty()) lines.addElement(text);
        return lines;
    }

    protected void pointerPressed(int px, int py) {
        int height = getHeight();
        int width = getWidth();
        boolean showReplyBanner = (replyingToMessage != null);

        // Check if tapped on reply banner close button [X]
        if (showReplyBanner) {
            int bannerY = height - inputHeight - replyBannerHeight;
            if (py >= bannerY && py < bannerY + replyBannerHeight) {
                if (px >= width - 35) {
                    replyingToMessage = null;
                    repaint();
                    return;
                }
            }
        }

        // Check if tapped in input bar
        int inputY = height - inputHeight;
        if (py >= inputY) {
            if (selectedMessageIndex != -1) {
                startReplyToSelected();
            } else {
                promptTextInput();
            }
            return;
        }

        // Check if tapped on any message bubble
        for (int i = 0; i < renderedBubbleBounds.size(); i++) {
            int[] bounds = (int[]) renderedBubbleBounds.elementAt(i);
            int bx = bounds[0];
            int by = bounds[1];
            int bw = bounds[2];
            int bh = bounds[3];
            int idx = bounds[4];

            if (px >= bx && px <= bx + bw && py >= by && py <= by + bh) {
                if (selectedMessageIndex == idx) {
                    startReplyToSelected();
                } else {
                    selectedMessageIndex = idx;
                    updateCommandList();
                    repaint();
                }
                return;
            }
        }

        // Tapped empty background
        if (selectedMessageIndex != -1) {
            selectedMessageIndex = -1;
            updateCommandList();
            repaint();
        }
    }

    protected void keyPressed(int keyCode) {
        int gameAction = getGameAction(keyCode);
        if (gameAction == UP) {
            if (selectedMessageIndex == -1) {
                Vector msgs = ChatStore.getInstance().getMessages(contact.getJid());
                if (msgs != null && !msgs.isEmpty()) {
                    selectedMessageIndex = msgs.size() - 1;
                    ensureSelectedVisible(selectedMessageIndex, false);
                    updateCommandList();
                    repaint();
                } else {
                    scrollOffset = Math.max(0, scrollOffset - 35);
                    if (scrollOffset <= 20 && hasMoreOlder && !isLoadingOlder) {
                        loadOlderMessagesIfNeeded();
                    }
                    repaint();
                }
            } else {
                int[] yh = getBubbleYandH(selectedMessageIndex);
                if (yh != null) {
                    int targetY = yh[0];
                    int msgTop = Math.max(0, targetY - 10);
                    // If the top of this big bubble is still scrolled off-screen above, scroll up within it first!
                    if (scrollOffset > msgTop + 5) {
                        scrollOffset = Math.max(msgTop, scrollOffset - 35);
                        if (scrollOffset <= 20 && hasMoreOlder && !isLoadingOlder) {
                            loadOlderMessagesIfNeeded();
                        }
                        repaint();
                        return;
                    }
                }

                // Top of current message is reached; navigate to previous message
                if (selectedMessageIndex > 0) {
                    selectedMessageIndex--;
                    ensureSelectedVisible(selectedMessageIndex, false);
                    if (selectedMessageIndex == 0) {
                        loadOlderMessagesIfNeeded();
                    }
                    repaint();
                } else {
                    loadOlderMessagesIfNeeded();
                }
            }
        } else if (gameAction == DOWN) {
            if (selectedMessageIndex != -1) {
                int chatAreaHeight = getHeight() - headerHeight - inputHeight - (replyingToMessage != null ? replyBannerHeight : 0);
                int[] yh = getBubbleYandH(selectedMessageIndex);
                if (yh != null) {
                    int targetY = yh[0];
                    int targetH = yh[1];
                    int maxMsgScroll = Math.max(0, targetY + targetH - chatAreaHeight + 10);
                    // If the bottom of this big bubble is still off-screen below, scroll down within it first!
                    if (scrollOffset < maxMsgScroll - 5) {
                        scrollOffset = Math.min(maxMsgScroll, scrollOffset + 35);
                        repaint();
                        return;
                    }
                }

                // Bottom of current message is reached; navigate to next message or exit selection
                Vector msgs = ChatStore.getInstance().getMessages(contact.getJid());
                if (msgs != null && selectedMessageIndex < msgs.size() - 1) {
                    selectedMessageIndex++;
                    ensureSelectedVisible(selectedMessageIndex, true);
                    repaint();
                } else {
                    selectedMessageIndex = -1;
                    updateCommandList();
                    repaint();
                }
            } else {
                int chatAreaHeight = getHeight() - headerHeight - inputHeight - (replyingToMessage != null ? replyBannerHeight : 0);
                int maxScroll = Math.max(0, totalContentHeight - chatAreaHeight + 10);
                scrollOffset = Math.min(maxScroll, scrollOffset + 35);
                repaint();
            }
        } else if (gameAction == LEFT) {
            // Fast page scroll up
            scrollOffset = Math.max(0, scrollOffset - 70);
            if (scrollOffset <= 20 && hasMoreOlder && !isLoadingOlder) {
                loadOlderMessagesIfNeeded();
            }
            repaint();
        } else if (gameAction == RIGHT) {
            // Fast page scroll down
            int chatAreaHeight = getHeight() - headerHeight - inputHeight - (replyingToMessage != null ? replyBannerHeight : 0);
            int maxScroll = Math.max(0, totalContentHeight - chatAreaHeight + 10);
            scrollOffset = Math.min(maxScroll, scrollOffset + 70);
            repaint();
        } else if (gameAction == FIRE || keyCode == -5 || keyCode == 10) {
            if (System.currentTimeMillis() - screenOpenTime < 600) {
                return;
            }
            if (isRecordingAudio) {
                toggleVoiceRecording();
            } else if (selectedMessageIndex != -1) {
                startReplyToSelected();
            } else {
                promptTextInput();
            }
        } else if (keyCode == -11) { // Clear / Escape key
            if (selectedMessageIndex != -1) {
                selectedMessageIndex = -1;
                updateCommandList();
                repaint();
            } else if (replyingToMessage != null) {
                replyingToMessage = null;
                repaint();
            }
        }
    }

    private int[] getBubbleYandH(int index) {
        Vector msgs = ChatStore.getInstance().getMessages(contact.getJid());
        if (msgs == null || index < 0 || index >= msgs.size()) return null;

        Font font = Theme.FONT_CHAT_MSG;
        int maxBubbleW = getWidth() - 50;
        int targetY = 10;
        if (isLoadingOlder) targetY += OLDER_LOADING_INDICATOR_H;

        for (int i = 0; i < msgs.size(); i++) {
            ChatMessage msg = (ChatMessage) msgs.elementAt(i);
            int bubbleH = calculateBubbleHeight(msg, font, maxBubbleW);

            if (i == index) {
                return new int[]{ targetY, bubbleH };
            }
            targetY += bubbleH + 6;
        }
        return null;
    }

    private void ensureSelectedVisible(int index, boolean fromAbove) {
        int[] yh = getBubbleYandH(index);
        if (yh == null) return;
        int targetY = yh[0];
        int targetH = yh[1];
        int chatAreaHeight = getHeight() - headerHeight - inputHeight - (replyingToMessage != null ? replyBannerHeight : 0);

        if (targetH > chatAreaHeight) {
            // Big message: align to top if entering from above, or bottom if entering from below
            if (fromAbove) {
                scrollOffset = Math.max(0, targetY - 10);
            } else {
                scrollOffset = Math.max(0, targetY + targetH - chatAreaHeight + 10);
            }
        } else {
            // Normal message: ensure both ends fit in view
            if (targetY < scrollOffset) {
                scrollOffset = Math.max(0, targetY - 10);
            } else if (targetY + targetH > scrollOffset + chatAreaHeight) {
                scrollOffset = targetY + targetH - chatAreaHeight + 10;
            }
        }
    }

    private void startReplyToSelected() {
        Vector msgs = ChatStore.getInstance().getMessages(contact.getJid());
        if (selectedMessageIndex >= 0 && selectedMessageIndex < msgs.size()) {
            replyingToMessage = (ChatMessage) msgs.elementAt(selectedMessageIndex);
            selectedMessageIndex = -1;
            updateCommandList();
            promptTextInput();
        }
    }

    private void promptTextInput() {
        String titleName = contact.getName();
        if (titleName == null || titleName.trim().length() == 0
            || titleName.indexOf('\u2219') >= 0 || titleName.indexOf('\u00B7') >= 0) {
            titleName = contact.getJid().getUser();
        }
        titleName = StringUtil.formatPhoneNumber(titleName);

        String boxTitle = Text.get("Mensagem para ") + titleName;
        if (replyingToMessage != null) {
            String qAuthor = replyingToMessage.isFromMe() ? Text.get("Você") :
                (replyingToMessage.getSenderName() != null && replyingToMessage.getSenderName().length() > 0 ? replyingToMessage.getSenderName() :
                (replyingToMessage.getParticipant() != null ? StringUtil.formatPhoneNumber(replyingToMessage.getParticipant()) : contact.getName()));
            qAuthor = StringUtil.formatPhoneNumber(qAuthor);
            boxTitle = Text.get("Resp a ") + qAuthor;
        }

        final TextBox tb = new TextBox(boxTitle, "", 500, TextField.ANY);
        Command send = new Command(Text.get("Enviar"), Command.OK, 1);
        Command cancel = new Command(Text.get("Cancelar"), Command.CANCEL, 2);
        tb.addCommand(send);
        tb.addCommand(cancel);
        tb.setCommandListener(new CommandListener() {
            public void commandAction(Command c, Displayable d) {
                if (c.getCommandType() == Command.OK) {
                    String text = tb.getString().trim();
                    if (text.length() > 0) {
                        sendMessage(text, replyingToMessage);
                        replyingToMessage = null;
                    }
                }
                controller.setDisplayable(ConversationCanvas.this);
            }
        });
        controller.setDisplayable(tb);
    }

    private void sendMessage(final String text, final ChatMessage quotedMsg) {
        long now = System.currentTimeMillis();
        Vector existing = ChatStore.getInstance().getMessages(contact.getJid());
        if (existing != null && !existing.isEmpty()) {
            ChatMessage last = (ChatMessage) existing.lastElement();
            if (last != null && last.getTimestamp() >= now) {
                now = last.getTimestamp() + 1000;
            }
        }
        final String msgId = "msg_" + now;
        final ChatMessage msg = new ChatMessage(msgId, contact.getJid(), text, now, true);
        if (quotedMsg != null) {
            msg.setQuotedId(quotedMsg.getId());
            msg.setQuotedText(quotedMsg.getText());
            String qAuthor = quotedMsg.isFromMe() ? Text.get("Você") :
                (quotedMsg.getSenderName() != null && quotedMsg.getSenderName().length() > 0 ? quotedMsg.getSenderName() :
                (quotedMsg.getParticipant() != null ? StringUtil.formatPhoneNumber(quotedMsg.getParticipant()) : contact.getName()));
            qAuthor = StringUtil.formatPhoneNumber(qAuthor);
            msg.setQuotedSender(qAuthor);
        }
        ChatStore.getInstance().addMessage(msg);
        ChatStore.getInstance().bumpContactToTop(contact.getJid(), text);
        SDCardMessageStore.getInstance().appendMessage(msg);
        scrollOffset = 99999;
        repaint();

        new Thread(new Runnable() {
            public void run() {
                BaileysBridgeClient.getInstance().sendMessage(contact.getJid().getRawString(), text, quotedMsg);
            }
        }).start();
    }

    public void commandAction(Command c, Displayable d) {
        if (c == sendCmd) {
            promptTextInput();
        } else if (c == selectCmd) {
            Vector msgs = ChatStore.getInstance().getMessages(contact.getJid());
            if (msgs != null && !msgs.isEmpty()) {
                selectedMessageIndex = msgs.size() - 1;
                ensureSelectedVisible(selectedMessageIndex, false);
                updateCommandList();
                repaint();
            }
        } else if (c == replyCmd) {
            startReplyToSelected();
        } else if (c == cancelSelectCmd) {
            selectedMessageIndex = -1;
            updateCommandList();
            repaint();
        } else if (c == voiceCmd) {
            toggleVoiceRecording();
        } else if (c == backCmd) {
            if (selectedMessageIndex != -1) {
                selectedMessageIndex = -1;
                updateCommandList();
                repaint();
            } else if (replyingToMessage != null) {
                replyingToMessage = null;
                repaint();
            } else {
                controller.showChatList();
            }
        }
    }

    private void toggleVoiceRecording() {
        VoiceAudioEngine engine = VoiceAudioEngine.getInstance();
        if (!engine.isRecording()) {
            isRecordingAudio = true;
            repaint();
            engine.startRecording(this);
        } else {
            isRecordingAudio = false;
            repaint();
            engine.stopRecording(this);
        }
    }

    public void onRecordFinished(final byte[] audioData) {
        if (audioData != null && audioData.length > 0) {
            final String noteText = Text.get("\uD83C\uDFA7 Mensagem de Voz");
            long now = System.currentTimeMillis();
            Vector existing = ChatStore.getInstance().getMessages(contact.getJid());
            if (existing != null && !existing.isEmpty()) {
                ChatMessage last = (ChatMessage) existing.lastElement();
                if (last != null && last.getTimestamp() >= now) {
                    now = last.getTimestamp() + 1000;
                }
            }
            final String msgId = "audio_" + now;
            final ChatMessage msg = new ChatMessage(msgId, contact.getJid(), noteText, now, true);
            ChatStore.getInstance().addMessage(msg);
            ChatStore.getInstance().bumpContactToTop(contact.getJid(), noteText);
            SDCardMessageStore.getInstance().appendMessage(msg);
            scrollOffset = 99999;
            repaint();

            new Thread(new Runnable() {
                public void run() {
                    BaileysBridgeClient.getInstance().sendAudio(contact.getJid().getRawString(), audioData);
                }
            }).start();
        }
    }

    public void onRecordError(String message) {
        isRecordingAudio = false;
        repaint();
    }
}
