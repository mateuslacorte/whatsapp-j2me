package com.whatsapp.storage;

import javax.microedition.rms.RecordStore;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.Random;

/**
 * Account preferences and authentication store backed by J2ME RMS (RecordStore).
 */
public class AccountStore {

    private static final String RS_NAME = "wa_account";

    public static final String BRIDGE_HOST = "wpp.j2me.lacorte.dev";
    public static final int BRIDGE_PORT = 443;
    public static final String BRIDGE_PATH = "/server/whatsapp";
    public static final int CALL_PORT = 5223;

    private String countryCode = "55";
    private String phoneNumber = "";
    private String pushName = "BlackBerry User";
    private String authToken = "";
    private String deviceId = null;
    private boolean registered = false;

    private static AccountStore instance;

    private AccountStore() {
        deviceId = generateUniqueDeviceId();
        load();
    }

    public static synchronized AccountStore getInstance() {
        if (instance == null) {
            instance = new AccountStore();
        }
        return instance;
    }

    public static String generateUniqueDeviceId() {
        Random rnd = new Random(System.currentTimeMillis());
        int val = Math.abs(rnd.nextInt()) & 0xFFFFFF;
        String hex = Integer.toHexString(val);
        while (hex.length() < 6) {
            hex = "0" + hex;
        }
        return "wpp_" + hex;
    }

    public synchronized void load() {
        RecordStore rs = null;
        try {
            rs = RecordStore.openRecordStore(RS_NAME, true);
            if (rs.getNumRecords() > 0) {
                byte[] data = rs.getRecord(1);
                DataInputStream dis = new DataInputStream(new ByteArrayInputStream(data));
                countryCode = dis.readUTF();
                phoneNumber = dis.readUTF();
                pushName = dis.readUTF();
                authToken = dis.readUTF();
                dis.readUTF();
                dis.readInt();
                dis.readInt();
                registered = dis.readBoolean();
                if (dis.available() > 0) {
                    deviceId = dis.readUTF();
                }
            }
        } catch (Exception e) {
        } finally {
            if (rs != null) {
                try { rs.closeRecordStore(); } catch (Exception ignored) {}
            }
        }

        // If no device ID yet or if still set to old prefix or fixed ID, randomize it and persist with wpp_
        if (deviceId == null || deviceId.trim().length() == 0 || deviceId.startsWith("bb_") || deviceId.indexOf("bold") >= 0) {
            deviceId = generateUniqueDeviceId();
            save();
        }
    }

    public synchronized void save() {
        RecordStore rs = null;
        try {
            rs = RecordStore.openRecordStore(RS_NAME, true);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            dos.writeUTF(countryCode != null ? countryCode : "");
            dos.writeUTF(phoneNumber != null ? phoneNumber : "");
            dos.writeUTF(pushName != null ? pushName : "BlackBerry User");
            dos.writeUTF(authToken != null ? authToken : "");
            dos.writeUTF(BRIDGE_HOST);
            dos.writeInt(BRIDGE_PORT);
            dos.writeInt(CALL_PORT);
            dos.writeBoolean(registered);
            dos.writeUTF(deviceId != null ? deviceId : generateUniqueDeviceId());
            byte[] data = baos.toByteArray();
            if (rs.getNumRecords() == 0) {
                rs.addRecord(data, 0, data.length);
            } else {
                rs.setRecord(1, data, 0, data.length);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (rs != null) {
                try { rs.closeRecordStore(); } catch (Exception ignored) {}
            }
        }
    }

    public String getCountryCode() { return countryCode; }
    public void setCountryCode(String cc) { this.countryCode = cc; }

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String pn) { this.phoneNumber = pn; }

    public String getFullJidString() {
        return countryCode + phoneNumber + "@s.whatsapp.net";
    }

    public String getPushName() { return pushName; }
    public void setPushName(String name) { this.pushName = name; }

    public String getAuthToken() { return authToken; }
    public void setAuthToken(String token) { this.authToken = token; }

    public String getServerHost() { return BRIDGE_HOST; }
    public int getServerPort() { return BRIDGE_PORT; }
    public String getServerPath() { return BRIDGE_PATH; }
    public int getCallPort() { return CALL_PORT; }

    public String getBaseUrl() {
        String base;
        if (BRIDGE_PORT == 443) {
            base = "https://" + BRIDGE_HOST;
        } else if (BRIDGE_PORT == 80) {
            base = "http://" + BRIDGE_HOST;
        } else {
            base = "https://" + BRIDGE_HOST + ":" + BRIDGE_PORT;
        }
        return base + BRIDGE_PATH;
    }

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String id) { this.deviceId = id; }

    public boolean isRegistered() { return registered; }
    public void setRegistered(boolean reg) { this.registered = reg; }
}
