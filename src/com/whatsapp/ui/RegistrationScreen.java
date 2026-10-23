package com.whatsapp.ui;

import com.whatsapp.i18n.Text;

import com.whatsapp.net.RegistrationClient;
import com.whatsapp.storage.AccountStore;

import javax.microedition.lcdui.*;

/**
 * Account Registration and SMS Verification Screen.
 * Implements Step 1 (phone number entry) and Step 2 (SMS code verification).
 */
public class RegistrationScreen extends Form implements CommandListener, RegistrationClient.Callback {

    private ScreenController controller;

    private TextField ccField;
    private TextField phoneField;
    private TextField codeField;
    private StringItem statusItem;

    private Command requestCodeCmd;
    private Command verifyCodeCmd;
    private Command skipCmd;
    private Command exitCmd;

    public RegistrationScreen(ScreenController controller) {
        super(Text.get("Registro WhatsApp"));
        this.controller = controller;

        AccountStore acc = AccountStore.getInstance();

        append(Text.get("Bem-vindo ao WhatsApp para BlackBerry J2ME!\nInsira seu número com DDI e DDD:\n"));

        ccField = new TextField(Text.get("DDI (País):"), acc.getCountryCode(), 5, TextField.NUMERIC);
        phoneField = new TextField(Text.get("Telefone (com DDD):"), acc.getPhoneNumber(), 15, TextField.PHONENUMBER);
        codeField = new TextField(Text.get("Código SMS (6 dígitos):"), "", 6, TextField.NUMERIC);
        statusItem = new StringItem("Status:", Text.get("Pronto para registrar"));

        append(ccField);
        append(phoneField);
        append(codeField);
        append(statusItem);

        requestCodeCmd = new Command(Text.get("Pedir Código"), Command.OK, 1);
        verifyCodeCmd = new Command(Text.get("Verificar"), Command.ITEM, 2);
        skipCmd = new Command(Text.get("Pular Registro"), Command.SCREEN, 3);
        exitCmd = new Command(Text.get("Sair"), Command.EXIT, 4);

        addCommand(requestCodeCmd);
        addCommand(verifyCodeCmd);
        addCommand(skipCmd);
        addCommand(exitCmd);

        setCommandListener(this);
    }

    public void commandAction(Command c, Displayable d) {
        if (c == requestCodeCmd) {
            String cc = ccField.getString().trim();
            String phone = phoneField.getString().trim();
            if (phone.length() == 0) {
                statusItem.setText(Text.get("Por favor digite o telefone."));
                return;
            }
            statusItem.setText(Text.get("Solicitando código SMS..."));
            RegistrationClient.requestCode(cc, phone, "sms", this);
        } else if (c == verifyCodeCmd) {
            String cc = ccField.getString().trim();
            String phone = phoneField.getString().trim();
            String code = codeField.getString().trim();
            if (code.length() == 0) {
                statusItem.setText(Text.get("Digite o código recebido."));
                return;
            }
            statusItem.setText(Text.get("Verificando código..."));
            RegistrationClient.verifyCode(cc, phone, code, this);
        } else if (c == skipCmd) {
            AccountStore.getInstance().setRegistered(true);
            AccountStore.getInstance().save();
            controller.showChatList();
        } else if (c == exitCmd) {
            controller.exitApp();
        }
    }

    public void onSuccess(String token, String message) {
        statusItem.setText(Text.get("Sucesso: ") + message);
        if (token != null) {
            controller.showChatList();
        }
    }

    public void onError(String message) {
        statusItem.setText(Text.get("Erro: ") + message);
    }
}
