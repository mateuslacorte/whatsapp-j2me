package com.whatsapp.net;

import javax.microedition.io.Connector;
import javax.microedition.io.HttpConnection;
import javax.microedition.io.SocketConnection;
import java.io.IOException;

/**
 * Universal network connection utility for J2ME.
 * Automatically adapts URLs for BlackBerry devices (enabling Wi-Fi and direct TCP)
 * while maintaining pure standard MIDP 2.0 behavior for Symbian, Nokia, and other devices.
 */
public class NetworkUtil {

    private static Boolean isBb = null;
    // Preferred transport mode for BlackBerry:
    // 0 = Wi-Fi direct shorthand (;interface=wifi)
    // 1 = Wi-Fi direct full (;deviceside=true;interface=wifi)
    // 2 = Direct TCP / Cellular APN (;deviceside=true)
    // 3 = Standard / BIS (no suffix)
    private static int preferredMode = 0;
    private static final int TOTAL_MODES = 4;

    public static boolean isBlackBerry() {
        if (isBb != null) {
            return isBb.booleanValue();
        }
        boolean bb = false;
        try {
            String platform = System.getProperty("microedition.platform");
            if (platform != null) {
                String p = platform.toLowerCase();
                if (p.indexOf("blackberry") >= 0 || p.indexOf("rim") >= 0) {
                    bb = true;
                }
            }
            if (!bb && System.getProperty("blackberry.device") != null) {
                bb = true;
            }
        } catch (Throwable t) {}
        isBb = new Boolean(bb);
        return bb;
    }

    public static String getUrlForMode(String url, int mode) {
        if (!isBlackBerry()) {
            return url;
        }
        if (mode == 0) {
            return url + ";interface=wifi";
        } else if (mode == 1) {
            return url + ";deviceside=true;interface=wifi";
        } else if (mode == 2) {
            return url + ";deviceside=true";
        } else {
            return url;
        }
    }

    public static HttpConnection openHttp(String url) throws IOException {
        return openHttp(url, Connector.READ_WRITE);
    }

    public static HttpConnection openHttp(String url, int mode) throws IOException {
        if (!isBlackBerry()) {
            return (HttpConnection) Connector.open(url, mode);
        }

        IOException lastEx = null;
        for (int i = 0; i < TOTAL_MODES; i++) {
            int attempt = (preferredMode + i) % TOTAL_MODES;
            String fullUrl = getUrlForMode(url, attempt);
            try {
                HttpConnection conn = (HttpConnection) Connector.open(fullUrl, mode);
                preferredMode = attempt;
                return conn;
            } catch (IOException e) {
                lastEx = e;
            } catch (SecurityException se) {
                throw se;
            } catch (Exception e) {
                lastEx = new IOException(e.getMessage() != null ? e.getMessage() : e.getClass().getName());
            }
        }
        if (lastEx != null) {
            throw lastEx;
        }
        throw new IOException("Unable to open HTTP connection");
    }

    public static HttpConnection openAndConnect(String url, String method, String userAgent) throws IOException {
        if (!isBlackBerry()) {
            HttpConnection conn = (HttpConnection) Connector.open(url, Connector.READ_WRITE);
            if (userAgent != null) {
                conn.setRequestProperty("User-Agent", userAgent);
            }
            conn.setRequestMethod(method != null ? method : HttpConnection.GET);
            return conn;
        }

        IOException lastEx = null;
        for (int i = 0; i < TOTAL_MODES; i++) {
            int attempt = (preferredMode + i) % TOTAL_MODES;
            String fullUrl = getUrlForMode(url, attempt);
            HttpConnection conn = null;
            try {
                conn = (HttpConnection) Connector.open(fullUrl, Connector.READ_WRITE);
                if (userAgent != null) {
                    conn.setRequestProperty("User-Agent", userAgent);
                }
                conn.setRequestMethod(method != null ? method : HttpConnection.GET);
                if (HttpConnection.GET.equals(method) || "HEAD".equals(method)) {
                    // Triggers the actual socket connection to verify transport
                    conn.getResponseCode();
                }
                preferredMode = attempt;
                return conn;
            } catch (IOException e) {
                if (conn != null) {
                    try { conn.close(); } catch (Exception ignored) {}
                }
                lastEx = e;
            } catch (SecurityException se) {
                if (conn != null) {
                    try { conn.close(); } catch (Exception ignored) {}
                }
                throw se;
            } catch (Exception e) {
                if (conn != null) {
                    try { conn.close(); } catch (Exception ignored) {}
                }
                lastEx = new IOException(e.getMessage() != null ? e.getMessage() : e.getClass().getName());
            }
        }
        if (lastEx != null) {
            throw lastEx;
        }
        throw new IOException("Unable to connect");
    }

    public static SocketConnection openSocket(String host, int port) throws IOException {
        String base = "socket://" + host + ":" + port;
        if (!isBlackBerry()) {
            return (SocketConnection) Connector.open(base, Connector.READ_WRITE, true);
        }

        IOException lastEx = null;
        for (int i = 0; i < TOTAL_MODES; i++) {
            int attempt = (preferredMode + i) % TOTAL_MODES;
            String fullUrl = getUrlForMode(base, attempt);
            try {
                SocketConnection conn = (SocketConnection) Connector.open(fullUrl, Connector.READ_WRITE, true);
                preferredMode = attempt;
                return conn;
            } catch (IOException e) {
                lastEx = e;
            } catch (SecurityException se) {
                throw se;
            } catch (Exception e) {
                lastEx = new IOException(e.getMessage() != null ? e.getMessage() : e.getClass().getName());
            }
        }
        if (lastEx != null) {
            throw lastEx;
        }
        throw new IOException("Unable to open socket");
    }
}
