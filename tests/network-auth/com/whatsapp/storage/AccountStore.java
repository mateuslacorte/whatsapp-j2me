package com.whatsapp.storage;

/** In-memory account fixture: production networking and Base64 are tested unchanged. */
public class AccountStore {
    public static String BRIDGE_USERNAME = "";
    public static String BRIDGE_PASSWORD = "";
    public static String base = "https://bridge.example.com/server/whatsapp";
    private static final AccountStore instance = new AccountStore();
    public static AccountStore getInstance() { return instance; }
    public String getBaseUrl() { return base; }
}
