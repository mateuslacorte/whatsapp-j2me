package com.whatsapp.ui;

import com.whatsapp.i18n.Text;

import com.whatsapp.model.Contact;
import com.whatsapp.protocol.Jid;
import com.whatsapp.storage.ChatStore;
import com.whatsapp.voice.VoiceCallManager;

import javax.microedition.lcdui.*;

/**
 * Voice Call Screen.
 * Displays real-time call status (Calling, Ringing, Connected, Ended)
 * and controls for Accept, Reject, End Call, and Mute.
 */
public class CallScreen extends Form implements CommandListener, VoiceCallManager.CallListener {

    private ScreenController controller;
    private Jid peerJid;
    private StringItem contactNameItem;
    private StringItem statusItem;
    private StringItem durationItem;

    private Command acceptCmd;
    private Command endCallCmd;
    private Command backCmd;

    private long callStartTime = 0;
    private boolean isOutgoing;

    public CallScreen(ScreenController controller, Jid peerJid, boolean isOutgoing) {
        super(Text.get("Chamada de Voz"));
        this.controller = controller;
        this.peerJid = peerJid;
        this.isOutgoing = isOutgoing;

        Contact contact = ChatStore.getInstance().getContact(peerJid);
        String name = contact != null ? contact.getName() : peerJid.getUser();

        contactNameItem = new StringItem(Text.get("Contato: "), name);
        statusItem = new StringItem("Status: ", isOutgoing ? Text.get("Chamando...") : Text.get("Chamada recebida..."));
        durationItem = new StringItem(Text.get("Duração: "), "--:--");

        append(contactNameItem);
        append(statusItem);
        append(durationItem);

        acceptCmd = new Command(Text.get("Atender"), Command.OK, 1);
        endCallCmd = new Command(Text.get("Encerrar"), Command.CANCEL, 2);
        backCmd = new Command(Text.get("Voltar"), Command.BACK, 3);

        if (!isOutgoing) {
            addCommand(acceptCmd);
        }
        addCommand(endCallCmd);
        addCommand(backCmd);

        setCommandListener(this);

        VoiceCallManager.getInstance().setCallListener(this);
        if (isOutgoing) {
            VoiceCallManager.getInstance().startCall(peerJid);
        }
    }

    public void commandAction(Command c, Displayable d) {
        if (c == acceptCmd) {
            removeCommand(acceptCmd);
            VoiceCallManager.getInstance().acceptCall();
        } else if (c == endCallCmd) {
            VoiceCallManager.getInstance().endCall(Text.get("Encerrado pelo usuário"));
            controller.showChatList();
        } else if (c == backCmd) {
            controller.showChatList();
        }
    }

    public void onCallStateChanged(int state, Jid peer, String reason) {
        if (state == VoiceCallManager.STATE_CALLING) {
            statusItem.setText(Text.get("Chamando..."));
        } else if (state == VoiceCallManager.STATE_RINGING) {
            statusItem.setText(Text.get("Tocando..."));
        } else if (state == VoiceCallManager.STATE_ACTIVE) {
            statusItem.setText(Text.get("Conectado (Audio ativo)"));
            callStartTime = System.currentTimeMillis();
            durationItem.setText("00:00");
        } else if (state == VoiceCallManager.STATE_ENDED || state == VoiceCallManager.STATE_IDLE) {
            statusItem.setText(Text.get("Chamada encerrada: ") + (reason != null ? reason : ""));
        }
    }
}
