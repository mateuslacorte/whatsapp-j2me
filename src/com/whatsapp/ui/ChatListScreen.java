package com.whatsapp.ui;

import com.whatsapp.i18n.Text;

import com.whatsapp.model.Contact;
import com.whatsapp.protocol.Jid;
import com.whatsapp.storage.AccountStore;
import com.whatsapp.storage.ChatStore;

import javax.microedition.lcdui.*;
import java.util.Vector;

/**
 * Main chat list and contacts screen.
 * Displays existing conversations, unread badges, and provides options
 * for new chat, voice call, profile/status, and settings.
 */
public class ChatListScreen extends List implements CommandListener {

    private ScreenController controller;
    private Command openChatCmd;
    private Command startCallCmd;
    private Command newChatCmd;
    private Command settingsCmd;
    private Command exitCmd;

    public ChatListScreen(ScreenController controller) {
        super("WhatsApp", List.IMPLICIT);
        this.controller = controller;

        openChatCmd = new Command(Text.get("Abrir Chat"), Command.ITEM, 1);
        startCallCmd = new Command(Text.get("Fazer Chamada"), Command.ITEM, 2);
        newChatCmd = new Command(Text.get("Novo Chat"), Command.SCREEN, 3);
        settingsCmd = new Command(Text.get("Configurações"), Command.SCREEN, 4);
        exitCmd = new Command(Text.get("Sair"), Command.EXIT, 5);

        addCommand(openChatCmd);
        addCommand(startCallCmd);
        addCommand(newChatCmd);
        addCommand(settingsCmd);
        addCommand(exitCmd);

        setSelectCommand(openChatCmd);
        setCommandListener(this);

        refresh();
    }

    public void refresh() {
        deleteAll();
        Vector contacts = ChatStore.getInstance().getContacts();
        for (int i = 0; i < contacts.size(); i++) {
            Contact c = (Contact) contacts.elementAt(i);
            append(c.toString(), null);
        }
    }

    public void commandAction(Command c, Displayable d) {
        if (c == openChatCmd || c == List.SELECT_COMMAND) {
            int idx = getSelectedIndex();
            if (idx >= 0) {
                Vector contacts = ChatStore.getInstance().getContacts();
                if (idx < contacts.size()) {
                    Contact contact = (Contact) contacts.elementAt(idx);
                    contact.setUnreadCount(0);
                    controller.showConversation(contact);
                }
            }
        } else if (c == startCallCmd) {
            int idx = getSelectedIndex();
            if (idx >= 0) {
                Vector contacts = ChatStore.getInstance().getContacts();
                if (idx < contacts.size()) {
                    Contact contact = (Contact) contacts.elementAt(idx);
                    controller.showCallScreen(contact.getJid(), true);
                }
            }
        } else if (c == newChatCmd) {
            showNewChatDialog();
        } else if (c == settingsCmd) {
            controller.showSettings();
        } else if (c == exitCmd) {
            controller.exitApp();
        }
    }

    private void showNewChatDialog() {
        final Form form = new Form(Text.get("Novo Chat"));
        final TextField phoneField = new TextField(Text.get("Número (com DDD):"), "", 20, TextField.PHONENUMBER);
        final TextField nameField = new TextField(Text.get("Nome:"), "", 30, TextField.ANY);
        form.append(phoneField);
        form.append(nameField);

        Command okCmd = new Command("OK", Command.OK, 1);
        Command cancelCmd = new Command(Text.get("Cancelar"), Command.CANCEL, 2);
        form.addCommand(okCmd);
        form.addCommand(cancelCmd);

        form.setCommandListener(new CommandListener() {
            public void commandAction(Command cmd, Displayable disp) {
                if (cmd.getCommandType() == Command.OK) {
                    String phone = phoneField.getString().trim();
                    String name = nameField.getString().trim();
                    if (phone.length() > 0) {
                        if (name.length() == 0) name = phone;
                        Contact c = new Contact(Jid.of(phone + "@s.whatsapp.net"), name, "Status");
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
