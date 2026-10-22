package com.whatsapp.ui;

import com.whatsapp.model.Contact;
import com.whatsapp.protocol.Jid;

import javax.microedition.lcdui.Displayable;

/**
 * Screen navigation controller interface.
 */
public interface ScreenController {
    void showQRCodeScreen();
    void showRegistration();
    void showChatList();
    void showConversation(Contact contact);
    void showCallScreen(Jid peerJid, boolean isOutgoing);
    void showSettings();
    void setDisplayable(Displayable d);
    void exitApp();
}
