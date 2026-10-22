package com.whatsapp.ui;

import com.whatsapp.model.Contact;
import com.whatsapp.net.BaileysBridgeClient;
import com.whatsapp.protocol.Jid;
import com.whatsapp.storage.ChatStore;
import com.whatsapp.util.StringUtil;

import javax.microedition.lcdui.*;
import java.util.Vector;

/**
 * Custom Canvas-rendered Chat List in authentic WhatsApp design.
 * Features real WhatsApp contact synchronization, smooth trackpad scrolling, and instant loading.
 */
public class ChatListCanvas extends Canvas implements CommandListener {

    private ScreenController controller;
    private int selectedIndex = 0;
    private int topIndex = 0;
    private int rowHeight = 52;
    private int headerHeight = Theme.getHeaderHeight(true);
    private boolean isLoading = false;

    private Command openChatCmd;
    private Command refreshCmd;
    private Command newChatCmd;
    private Command exitCmd;

    public ChatListCanvas(ScreenController controller) {
        this.controller = controller;

        openChatCmd = new Command("Abrir Chat", Command.ITEM, 1);
        refreshCmd = new Command("Atualizar", Command.SCREEN, 2);
        newChatCmd = new Command("Novo Chat", Command.SCREEN, 3);
        exitCmd = new Command("Sair", Command.EXIT, 4);

        addCommand(openChatCmd);
        addCommand(refreshCmd);
        addCommand(newChatCmd);
        addCommand(exitCmd);

        setCommandListener(this);

        loadChatsFromServer();
    }

    public void refresh() {
        repaint();
    }

    public void loadChatsFromServer() {
        if (isLoading) return;
        isLoading = true;
        new Thread(new Runnable() {
            public void run() {
                try {
                    BaileysBridgeClient.getInstance().fetchChats();
                } catch (Exception ignored) {
                } finally {
                    isLoading = false;
                    repaint();
                }
            }
        }).start();
    }

    protected void paint(Graphics g) {
        int width = getWidth();
        int height = getHeight();

        // 1. WhatsApp Dark Teal Header
        Theme.drawWhatsAppHeader(g, width, headerHeight, "WhatsApp", "CHATS");

        // 2. Chat List Background
        g.setColor(Theme.COLOR_LIST_BG);
        g.fillRect(0, headerHeight, width, height - headerHeight);

        Vector contacts = ChatStore.getInstance().getContacts();
        if (contacts.isEmpty()) {
            g.setColor(Theme.COLOR_TEXT_MUTED);
            g.setFont(Theme.FONT_CHAT_MSG);
            if (isLoading) {
                g.drawString("Carregando conversas do celular...", width / 2, height / 2 - 12, Graphics.TOP | Graphics.HCENTER);
                g.drawString("Aguarde um instante...", width / 2, height / 2 + 10, Graphics.TOP | Graphics.HCENTER);
            } else {
                g.drawString("Aguardando conversas do WhatsApp...", width / 2, height / 2 - 12, Graphics.TOP | Graphics.HCENTER);
                g.drawString("Pressione 'Atualizar' ou 'Novo Chat'", width / 2, height / 2 + 10, Graphics.TOP | Graphics.HCENTER);
            }
            return;
        }

        // Clamp index
        if (selectedIndex >= contacts.size()) selectedIndex = contacts.size() - 1;
        if (selectedIndex < 0) selectedIndex = 0;

        int visibleRows = (height - headerHeight) / rowHeight;
        if (visibleRows <= 0) visibleRows = 1;

        if (selectedIndex >= topIndex + visibleRows) {
            topIndex = selectedIndex - visibleRows + 1;
        }
        if (selectedIndex < topIndex) {
            topIndex = selectedIndex;
        }

        int y = headerHeight;
        for (int i = topIndex; i < contacts.size() && y < height; i++) {
            Contact c = (Contact) contacts.elementAt(i);
            boolean isSelected = (i == selectedIndex);

            // Highlight selected row
            if (isSelected) {
                g.setColor(Theme.COLOR_SELECTION);
                g.fillRect(0, y, width, rowHeight);
            }

            // Circular Avatar Placeholder
            int avatarX = 12;
            int avatarY = y + 7;
            int avatarSize = 38;
            g.setColor(0x075E54);
            g.fillArc(avatarX, avatarY, avatarSize, avatarSize, 0, 360);
            g.setColor(Theme.COLOR_WHITE);

            String displayName = c.getName();
            if (displayName == null || displayName.trim().length() == 0
                || displayName.indexOf('\u2219') >= 0 || displayName.indexOf('\u00B7') >= 0) {
                displayName = c.getJid().getUser();
            }
            displayName = StringUtil.formatPhoneNumber(displayName);

            String initial = displayName.length() > 0 ? displayName.substring(0, 1).toUpperCase() : "?";
            g.setFont(Theme.FONT_CHAT_TITLE);
            g.drawString(initial, avatarX + (avatarSize / 2), avatarY + 10, Graphics.TOP | Graphics.HCENTER);

            // Contact Name
            int textX = avatarX + avatarSize + 12;
            int maxNameW = width - textX - 45;
            g.setFont(Theme.FONT_CHAT_TITLE);
            g.setColor(Theme.COLOR_TEXT_MAIN);

            if (Theme.FONT_CHAT_TITLE.stringWidth(displayName) > maxNameW) {
                while (displayName.length() > 3 && Theme.FONT_CHAT_TITLE.stringWidth(displayName + "...") > maxNameW) {
                    displayName = displayName.substring(0, displayName.length() - 1);
                }
                displayName += "...";
            }
            g.drawString(displayName, textX, y + 8, Graphics.TOP | Graphics.LEFT);

            int unread = c.getUnreadCount();

            // Subtitle / Preview
            g.setFont(Theme.FONT_CHAT_MSG);
            g.setColor(Theme.COLOR_TEXT_MUTED);
            String snippet = c.getStatusText();
            if (snippet == null || snippet.length() == 0) snippet = c.getJid().getUser();
            int maxSnippetW = width - textX - (unread > 0 ? 45 : 15);
            if (Theme.FONT_CHAT_MSG.stringWidth(snippet) > maxSnippetW) {
                while (snippet.length() > 3 && Theme.FONT_CHAT_MSG.stringWidth(snippet + "...") > maxSnippetW) {
                    snippet = snippet.substring(0, snippet.length() - 1);
                }
                snippet += "...";
            }
            g.drawString(snippet, textX, y + 28, Graphics.TOP | Graphics.LEFT);

            // Unread Count Badge
            if (unread > 0) {
                int displayCount = unread > 99 ? 99 : unread;
                String countStr = String.valueOf(displayCount);

                int badgeH = 18;
                int textW = Theme.FONT_BADGE.stringWidth(countStr);
                int badgeW = (displayCount < 10) ? badgeH : Math.max(badgeH, textW + 8);
                int badgeX = width - badgeW - 12;
                int badgeY = y + (rowHeight - badgeH) / 2;

                // High-contrast, perfectly round WhatsApp green bubble
                g.setColor(Theme.COLOR_BADGE_BG);
                if (displayCount < 10) {
                    // Mathematically perfect circle for single digit
                    g.fillArc(badgeX, badgeY, badgeH, badgeH, 0, 360);
                } else {
                    // Mathematically perfect stadium/capsule pill for double digits (capped at 99)
                    g.fillArc(badgeX, badgeY, badgeH, badgeH, 90, 180);
                    g.fillArc(badgeX + badgeW - badgeH, badgeY, badgeH, badgeH, 270, 180);
                    g.fillRect(badgeX + badgeH / 2, badgeY, badgeW - badgeH, badgeH);
                    g.fillRoundRect(badgeX, badgeY, badgeW, badgeH, badgeH, badgeH);
                }

                // Crisp, high-contrast bold text centered horizontally and vertically
                g.setColor(Theme.COLOR_BADGE_TEXT);
                g.setFont(Theme.FONT_BADGE);
                int fontH = Theme.FONT_BADGE.getHeight();
                int textY = badgeY + (badgeH - fontH) / 2;
                g.drawString(countStr, badgeX + (badgeW / 2), textY, Graphics.TOP | Graphics.HCENTER);
            }

            // Bottom Divider Line
            g.setColor(Theme.COLOR_DIVIDER);
            g.drawLine(textX, y + rowHeight - 1, width, y + rowHeight - 1);

            y += rowHeight;
        }

        // Draw vertical scroll indicator if list exceeds visible height
        if (contacts.size() > visibleRows) {
            int availableH = height - headerHeight;
            int barH = Math.max(16, availableH * visibleRows / contacts.size());
            int barY = headerHeight + ((availableH - barH) * topIndex / (contacts.size() - visibleRows));
            g.setColor(0xCCCCCC);
            g.fillRect(width - 4, barY, 3, barH);
        }
    }

    protected void keyPressed(int keyCode) {
        int gameAction = getGameAction(keyCode);
        Vector contacts = ChatStore.getInstance().getContacts();
        if (gameAction == UP) {
            if (selectedIndex > 0) {
                selectedIndex--;
                repaint();
            }
        } else if (gameAction == DOWN) {
            if (selectedIndex < contacts.size() - 1) {
                selectedIndex++;
                repaint();
            }
        } else if (gameAction == FIRE || keyCode == -5 || keyCode == 10) {
            openSelectedChat();
        }
    }

    private long lastOpenChatTime = 0;

    private void openSelectedChat() {
        long now = System.currentTimeMillis();
        if (now - lastOpenChatTime < 600) {
            return;
        }
        lastOpenChatTime = now;
        Vector contacts = ChatStore.getInstance().getContacts();
        if (selectedIndex >= 0 && selectedIndex < contacts.size()) {
            Contact c = (Contact) contacts.elementAt(selectedIndex);
            c.setUnreadCount(0);
            ChatStore.getInstance().clearUnread(c.getJid());
            controller.showConversation(c);
        }
    }

    public void commandAction(Command c, Displayable d) {
        if (c == openChatCmd) {
            openSelectedChat();
        } else if (c == refreshCmd) {
            loadChatsFromServer();
        } else if (c == newChatCmd) {
            showNewChatDialog();
        } else if (c == exitCmd) {
            controller.exitApp();
        }
    }

    private void showNewChatDialog() {
        final Form form = new Form("Novo Chat WhatsApp");
        final TextField phoneField = new TextField("Numero com DDD (ex: 5511999998888):", "", 25, TextField.PHONENUMBER);
        final TextField nameField = new TextField("Nome do Contato:", "", 30, TextField.ANY);
        form.append(phoneField);
        form.append(nameField);

        Command okCmd = new Command("Criar Chat", Command.OK, 1);
        Command cancelCmd = new Command("Cancelar", Command.CANCEL, 2);
        form.addCommand(okCmd);
        form.addCommand(cancelCmd);

        form.setCommandListener(new CommandListener() {
            public void commandAction(Command cmd, Displayable disp) {
                if (cmd.getCommandType() == Command.OK) {
                    String phone = phoneField.getString().trim();
                    String name = nameField.getString().trim();
                    if (phone.length() > 0) {
                        if (name.length() == 0) name = phone;
                        Contact c = new Contact(Jid.of(phone + "@s.whatsapp.net"), name, "");
                        ChatStore.getInstance().addContact(c);
                        refresh();
                        controller.showConversation(c);
                        return;
                    }
                }
                controller.showChatList();
            }
        });
        controller.setDisplayable(form);
    }
}
