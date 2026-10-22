package com.whatsapp.util;

/**
 * Lightweight Base64 encoder and decoder compliant with CLDC 1.1.
 */
public class Base64 {

    private static final char[] ENCODE_TABLE = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".toCharArray();
    private static final byte[] DECODE_TABLE = new byte[128];

    static {
        for (int i = 0; i < 128; i++) {
            DECODE_TABLE[i] = -1;
        }
        for (int i = 0; i < 64; i++) {
            DECODE_TABLE[ENCODE_TABLE[i]] = (byte) i;
        }
    }

    private Base64() {}

    public static String encode(byte[] data) {
        if (data == null) return "";
        int len = data.length;
        StringBuffer sb = new StringBuffer(((len + 2) / 3) * 4);
        int i = 0;
        while (i < len) {
            int b1 = data[i++] & 0xFF;
            int b2 = i < len ? data[i++] & 0xFF : -1;
            int b3 = i < len ? data[i++] & 0xFF : -1;

            sb.append(ENCODE_TABLE[b1 >>> 2]);
            if (b2 != -1) {
                sb.append(ENCODE_TABLE[((b1 & 0x03) << 4) | (b2 >>> 4)]);
                if (b3 != -1) {
                    sb.append(ENCODE_TABLE[((b2 & 0x0F) << 2) | (b3 >>> 6)]);
                    sb.append(ENCODE_TABLE[b3 & 0x3F]);
                } else {
                    sb.append(ENCODE_TABLE[(b2 & 0x0F) << 2]);
                    sb.append('=');
                }
            } else {
                sb.append(ENCODE_TABLE[(b1 & 0x03) << 4]);
                sb.append("==");
            }
        }
        return sb.toString();
    }

    public static byte[] decode(String s) {
        if (s == null) return new byte[0];
        int len = s.length();
        int pad = 0;
        if (len > 0 && s.charAt(len - 1) == '=') pad++;
        if (len > 1 && s.charAt(len - 2) == '=') pad++;
        int byteLen = (len * 3) / 4 - pad;
        byte[] bytes = new byte[byteLen];
        int byteIdx = 0;
        int i = 0;
        while (i < len) {
            char c0 = s.charAt(i++);
            char c1 = s.charAt(i++);
            char c2 = i < len ? s.charAt(i++) : '=';
            char c3 = i < len ? s.charAt(i++) : '=';

            int v0 = c0 < 128 ? DECODE_TABLE[c0] : -1;
            int v1 = c1 < 128 ? DECODE_TABLE[c1] : -1;
            int v2 = c2 < 128 ? DECODE_TABLE[c2] : -1;
            int v3 = c3 < 128 ? DECODE_TABLE[c3] : -1;

            if (byteIdx < byteLen) bytes[byteIdx++] = (byte) ((v0 << 2) | (v1 >>> 4));
            if (byteIdx < byteLen) bytes[byteIdx++] = (byte) (((v1 & 0x0F) << 4) | (v2 >>> 2));
            if (byteIdx < byteLen) bytes[byteIdx++] = (byte) (((v2 & 0x03) << 6) | v3);
        }
        return bytes;
    }
}
