package com.whatsapp;

import com.whatsapp.i18n.Text;

import com.whatsapp.model.ChatMessage;
import com.whatsapp.model.Contact;
import com.whatsapp.net.BaileysBridgeClient;
import com.whatsapp.net.ConnectionListener;
import com.whatsapp.net.SocketConnectionManager;
import com.whatsapp.protocol.Jid;
import com.whatsapp.protocol.ProtocolTreeNode;
import com.whatsapp.storage.AccountStore;
import com.whatsapp.storage.ChatStore;
import com.whatsapp.ui.*;
import com.whatsapp.voice.VoiceCallManager;

import javax.microedition.lcdui.Alert;
import javax.microedition.lcdui.AlertType;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;
import javax.microedition.midlet.MIDlet;

/**
 * WhatsApp J2ME MIDlet for BlackBerry and vintage phones.
 */
public class WhatsAppMIDlet extends MIDlet implements ScreenController, ConnectionListener, BaileysBridgeClient.MessageListener {

    private Display display;
    private ChatListCanvas chatListCanvas;
    private ConversationCanvas currentConversationCanvas;
    private CallScreen currentCallScreen;
    private QRCodeScreen qrCodeScreen;
    private boolean isAppInBackground = false;

    public WhatsAppMIDlet() {
        display = Display.getDisplay(this);
    }

    protected void startApp() {
        isAppInBackground = false;
        SocketConnectionManager.getInstance().addConnectionListener(this);
        BaileysBridgeClient.getInstance().setMessageListener(this);

        AccountStore acc = AccountStore.getInstance();
        if (!acc.isRegistered()) {
            showQRCodeScreen();
        } else {
            showChatList();
            BaileysBridgeClient.getInstance().startBackgroundSync();
        }
    }

    protected void pauseApp() {
        isAppInBackground = true;
    }

    protected void destroyApp(boolean unconditional) {
        BaileysBridgeClient.getInstance().stopBackgroundSync();
        SocketConnectionManager.getInstance().disconnect();
        VoiceCallManager.getInstance().endCall("App destroyed");
    }

    public void showQRCodeScreen() {
        currentConversationCanvas = null;
        ChatStore.getInstance().setActiveConversation(null);
        if (qrCodeScreen == null) {
            qrCodeScreen = new QRCodeScreen(this);
        } else {
            qrCodeScreen.reset(Text.get("Conecte seu WhatsApp"));
        }
        display.setCurrent(qrCodeScreen);
    }

    public void showRegistration() {
        RegistrationScreen reg = new RegistrationScreen(this);
        display.setCurrent(reg);
    }

    public void showChatList() {
        currentConversationCanvas = null;
        ChatStore.getInstance().setActiveConversation(null);
        if (chatListCanvas == null) {
            chatListCanvas = new ChatListCanvas(this);
        } else {
            chatListCanvas.refresh();
        }
        display.setCurrent(chatListCanvas);
    }

    public void showConversation(Contact contact) {
        if (contact != null) {
            contact.setUnreadCount(0);
            ChatStore.getInstance().setActiveConversation(contact.getJid());
            ChatStore.getInstance().clearUnread(contact.getJid());
        }
        currentConversationCanvas = new ConversationCanvas(this, contact);
        display.setCurrent(currentConversationCanvas);
    }

    public void showCallScreen(Jid peerJid, boolean isOutgoing) {
        currentCallScreen = new CallScreen(this, peerJid, isOutgoing);
        display.setCurrent(currentCallScreen);
    }

    public void showSettings() {
        SettingsScreen settings = new SettingsScreen(this);
        display.setCurrent(settings);
    }

    public void setDisplayable(Displayable d) {
        display.setCurrent(d);
    }

    public void exitApp() {
        destroyApp(false);
        notifyDestroyed();
    }

    // === BaileysBridgeClient Callbacks (Background Notifications) ===

    public void onNewMessage(final ChatMessage message) {
        try {
            display.vibrate(250);
        } catch (Throwable ignored) {}

        display.callSerially(new Runnable() {
            public void run() {
                if (currentConversationCanvas != null) {
                    currentConversationCanvas.refresh();
                }
                if (chatListCanvas != null) {
                    chatListCanvas.refresh();
                }

                if (isAppInBackground) {
                    Alert alert = new Alert("WhatsApp", message.getRemoteJid().getUser() + ": " + message.getText(), null, AlertType.INFO);
                    alert.setTimeout(3000);
                    display.setCurrent(alert);
                }
            }
        });
    }

    public void onCallReceived(final String callerJid, final String callId) {
        try {
            display.vibrate(1000);
            AlertType.ALARM.playSound(display);
        } catch (Throwable ignored) {}

        display.callSerially(new Runnable() {
            public void run() {
                showCallScreen(Jid.of(callerJid), false);
            }
        });
    }

    public void onSessionDisconnected() {
        System.out.println(">>> [SESSION DISCONNECTED] Returning to QRCodeScreen...");
        display.callSerially(new Runnable() {
            public void run() {
                AccountStore acc = AccountStore.getInstance();
                acc.setRegistered(false);
                acc.save();
                BaileysBridgeClient.getInstance().stopBackgroundSync();
                currentConversationCanvas = null;
                ChatStore.getInstance().setActiveConversation(null);

                if (qrCodeScreen == null) {
                    qrCodeScreen = new QRCodeScreen(WhatsAppMIDlet.this);
                } else {
                    qrCodeScreen.reset(Text.get("Sessão desconectada. Escaneie novamente."));
                }
                display.setCurrent(qrCodeScreen);
            }
        });
    }

    // === ConnectionListener Callbacks ===

    public void onConnected() {}
    public void onDisconnected(String reason) {}
    public void onAuthenticated() {}
    public void onAuthFailed(String reason) {}
    public void onNodeReceived(final ProtocolTreeNode node) {}
    public void onError(String message) {}
}
