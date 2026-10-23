package com.whatsapp.ui;

import com.whatsapp.i18n.Text;

import com.whatsapp.storage.AccountStore;

import javax.microedition.lcdui.*;

/**
 * Settings Screen.
 * Displays fixed server status (MacBook IP) and profile configurations.
 */
public class SettingsScreen extends Form implements CommandListener {

    private ScreenController controller;

    private TextField nameField;
    private TextField deviceIdField;
    private StringItem serverInfoItem;
    private Command saveCmd;
    private Command rePairCmd;
    private Command backCmd;

    public SettingsScreen(ScreenController controller) {
        super(Text.get("Configurações WhatsApp"));
        this.controller = controller;

        AccountStore acc = AccountStore.getInstance();

        nameField = new TextField(Text.get("Nome de exibição:"), acc.getPushName(), 30, TextField.ANY);
        deviceIdField = new TextField(Text.get("ID do Aparelho:"), acc.getDeviceId(), 30, TextField.ANY);
        serverInfoItem = new StringItem(Text.get("Servidor Baileys:"), acc.getBaseUrl());

        append(nameField);
        append(deviceIdField);
        append(serverInfoItem);

        saveCmd = new Command(Text.get("Salvar"), Command.OK, 1);
        rePairCmd = new Command(Text.get("Novo QR Code"), Command.ITEM, 2);
        backCmd = new Command(Text.get("Voltar"), Command.BACK, 3);

        addCommand(saveCmd);
        addCommand(rePairCmd);
        addCommand(backCmd);

        setCommandListener(this);
    }

    public void commandAction(Command c, Displayable d) {
        if (c == saveCmd) {
            AccountStore acc = AccountStore.getInstance();
            acc.setPushName(nameField.getString().trim());
            acc.setDeviceId(deviceIdField.getString().trim());
            acc.save();
            controller.showChatList();
        } else if (c == rePairCmd) {
            AccountStore acc = AccountStore.getInstance();
            acc.setRegistered(false);
            acc.save();
            controller.showQRCodeScreen();
        } else if (c == backCmd) {
            controller.showChatList();
        }
    }
}
