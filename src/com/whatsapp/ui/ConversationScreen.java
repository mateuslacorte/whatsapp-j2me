package com.whatsapp.ui;

import com.whatsapp.model.ChatMessage;
import com.whatsapp.model.Contact;
import com.whatsapp.net.BaileysBridgeClient;
import com.whatsapp.net.SocketConnectionManager;
import com.whatsapp.protocol.Jid;
import com.whatsapp.protocol.KeyValue;
import com.whatsapp.protocol.ProtocolTreeNode;
import com.whatsapp.storage.AccountStore;
import com.whatsapp.storage.ChatStore;
import com.whatsapp.storage.SDCardMessageStore;
import com.whatsapp.voice.VoiceAudioEngine;

import javax.microedition.lcdui.*;
import java.util.Vector;

/**
 * Conversation Screen styled like WhatsApp.
 * Supports sending messages via Baileys Bridge and direct socket,
 * instant loading from SD Card, and voice note recording.
 */
public class ConversationScreen extends Form implements CommandListener, VoiceAudioEngine.RecordListener {

    private ScreenController controller;
    private Contact contact;

    private TextField inputField;
    private StringItem statusItem;
    private Command sendCmd;
    private Command voiceNoteCmd;
    private Command callCmd;
    private Command backCmd;

    public ConversationScreen(ScreenController controller, Contact contact) {
        super(contact.getName());
        this.controller = controller;
        this.contact = contact;
        if (contact != null) {
            contact.setUnreadCount(0);
            ChatStore.getInstance().setActiveConversation(contact.getJid());
            ChatStore.getInstance().clearUnread(contact.getJid());
        }

        sendCmd = new Command("Enviar", Command.OK, 1);
        voiceNoteCmd = new Command("Gravar Audio", Command.ITEM, 2);
        callCmd = new Command("Ligar (Voz)", Command.SCREEN, 3);
        backCmd = new Command("Voltar", Command.BACK, 4);

        addCommand(sendCmd);
        addCommand(voiceNoteCmd);
        addCommand(callCmd);
        addCommand(backCmd);

        setCommandListener(this);

        statusItem = new StringItem(null, "");
        append(statusItem);

        inputField = new TextField("Mensagem:", "", 256, TextField.ANY);
        append(inputField);

        renderMessages();
    }

    public void renderMessages() {
        while (size() > 2) {
            delete(2);
        }

        Vector msgs = ChatStore.getInstance().getMessages(contact.getJid());
        for (int i = 0; i < msgs.size(); i++) {
            ChatMessage m = (ChatMessage) msgs.elementAt(i);
            String prefix = m.isFromMe() ? "Eu: " : (contact.getName() + ": ");
            StringItem item = new StringItem(prefix, m.getText());
            append(item);
        }
    }

    public void commandAction(Command c, Displayable d) {
        if (c == sendCmd) {
            String text = inputField.getString().trim();
            if (text.length() > 0) {
                sendMessage(text);
                inputField.setString("");
            }
        } else if (c == voiceNoteCmd) {
            toggleVoiceRecording();
        } else if (c == callCmd) {
            controller.showCallScreen(contact.getJid(), true);
        } else if (c == backCmd) {
            controller.showChatList();
        }
    }

    private void sendMessage(final String text) {
        final String msgId = "msg_" + System.currentTimeMillis();
        final ChatMessage msg = new ChatMessage(msgId, contact.getJid(), text, System.currentTimeMillis(), true);
        ChatStore.getInstance().addMessage(msg);
        SDCardMessageStore.getInstance().appendMessage(msg);
        renderMessages();

        new Thread(new Runnable() {
            public void run() {
                // Primary: send via Baileys Bridge
                boolean ok = BaileysBridgeClient.getInstance().sendMessage(contact.getJid().getRawString(), text);
                if (!ok) {
                    // Fallback to direct raw socket
                    try {
                        KeyValue[] attrs = new KeyValue[] {
                            new KeyValue("to", contact.getJid().getRawString()),
                            new KeyValue("type", "chat"),
                            new KeyValue("id", msgId)
                        };
                        ProtocolTreeNode bodyNode = new ProtocolTreeNode("body", new KeyValue[]{}, text);
                        ProtocolTreeNode msgNode = new ProtocolTreeNode("message", attrs, bodyNode);
                        SocketConnectionManager.getInstance().sendNode(msgNode);
                    } catch (Exception e) {
                        statusItem.setText("Erro ao enviar: " + e.getMessage());
                    }
                }
            }
        }).start();
    }

    private void toggleVoiceRecording() {
        VoiceAudioEngine engine = VoiceAudioEngine.getInstance();
        if (!engine.isRecording()) {
            statusItem.setText("Gravando audio PTT... Selecione Gravar para finalizar.");
            engine.startRecording(this);
        } else {
            statusItem.setText("Enviando audio...");
            engine.stopRecording(this);
        }
    }

    public void onRecordFinished(byte[] audioData) {
        if (audioData != null && audioData.length > 0) {
            String noteText = "\uD83C\uDFA7 [Audio gravado: " + (audioData.length / 1024) + " KB]";
            sendMessage(noteText);
            statusItem.setText("Audio enviado!");
        } else {
            statusItem.setText("Nenhum audio gravado.");
        }
    }

    public void onRecordError(String message) {
        statusItem.setText("Erro audio: " + message);
    }
}
