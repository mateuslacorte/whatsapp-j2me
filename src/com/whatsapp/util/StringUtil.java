package com.whatsapp.util;

import java.util.Calendar;
import java.util.Date;
import java.util.Vector;

/**
 * String utilities compatible with CLDC 1.1 / J2ME.
 */
public class StringUtil {

    private static Calendar sCalendar = Calendar.getInstance();

    private StringUtil() {}

    public static synchronized String formatMessageTime(long timestamp) {
        if (timestamp <= 0) return "";
        if (timestamp < 10000000000L) {
            timestamp *= 1000L;
        }
        sCalendar.setTime(new Date(timestamp));
        int hour = sCalendar.get(Calendar.HOUR_OF_DAY);
        int min = sCalendar.get(Calendar.MINUTE);
        return (hour < 10 ? "0" + hour : "" + hour) + ":" + (min < 10 ? "0" + min : "" + min);
    }

    public static String[] split(String original, char separator) {
        if (original == null) return new String[0];
        Vector nodes = new Vector();
        int index = original.indexOf(separator);
        while (index >= 0) {
            nodes.addElement(original.substring(0, index));
            original = original.substring(index + 1);
            index = original.indexOf(separator);
        }
        nodes.addElement(original);

        String[] result = new String[nodes.size()];
        for (int i = 0; i < nodes.size(); i++) {
            result[i] = (String) nodes.elementAt(i);
        }
        return result;
    }

    public static int lastIndexOf(String src, String target) {
        if (src == null || target == null) return -1;
        int tLen = target.length();
        int sLen = src.length();
        if (tLen > sLen) return -1;
        for (int i = sLen - tLen; i >= 0; i--) {
            if (src.substring(i, i + tLen).equals(target)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Checks if a string is a phone number, raw numeric JID, or unsaved contact.
     */
    public static boolean isNumericOrPhone(String s) {
        if (s == null) return false;
        String t = s.trim();
        if (t.length() == 0) return false;
        int digitCount = 0;
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (c >= '0' && c <= '9') {
                digitCount++;
            } else if (c != '+' && c != ' ' && c != '-' && c != '(' && c != ')' && c != '.' && c != '@') {
                return false;
            }
        }
        return digitCount >= 7;
    }

    /**
     * Extract only ASCII digits from string.
     */
    public static String extractDigits(String s) {
        if (s == null) return "";
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '0' && c <= '9') {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * Formats a phone number to WhatsApp Desktop format:
     * - Brazil 13 digits (5511922048647): +55 11 92204-8647
     * - Brazil 12 digits (556191771981):  +55 61 9177-1981
     * - Brazil 11 digits (11922048647):   +55 11 92204-8647
     * - Brazil 10 digits (6191771981):    +55 61 9177-1981
     * - US/Canada 11 digits (17377669339): +1 737 766-9339
     * - UK (447123456789):                +44 7123 456789
     * - Portugal (351912345678):          +351 912 345 678
     *
     * If the string is already formatted or is a saved contact name (contains letters),
     * it returns the original string unmodified.
     */
    public static String formatPhoneNumber(String raw) {
        if (raw == null) return "";
        String s = raw.trim();
        if (s.length() == 0) return "";

        // Groups or special JIDs should never be formatted as phone numbers
        if (s.indexOf("@g.us") >= 0 || (s.indexOf("-") >= 0 && s.indexOf("@") >= 0)) {
            return s;
        }

        // Strip JID suffix if present (@s.whatsapp.net, @lid, etc.)
        int atIdx = s.indexOf('@');
        if (atIdx >= 0) {
            s = s.substring(0, atIdx);
        }

        // If it contains letters or accented/unicode characters, it's a saved contact name!
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c > 127) {
                return raw;
            }
        }

        // If it already starts with '+' and has spaces, it is already formatted
        if (s.startsWith("+") && s.indexOf(' ') >= 0) {
            return s;
        }

        String d = extractDigits(s);
        int len = d.length();

        // 1. Brazil (+55)
        if (d.startsWith("55")) {
            if (len == 13) {
                // +55 AA 9XXXX-XXXX
                return "+55 " + d.substring(2, 4) + " " + d.substring(4, 9) + "-" + d.substring(9, 13);
            } else if (len == 12) {
                // +55 AA XXXX-XXXX
                return "+55 " + d.substring(2, 4) + " " + d.substring(4, 8) + "-" + d.substring(8, 12);
            }
        } else if (len == 11 && !d.startsWith("1")) {
            return "+55 " + d.substring(0, 2) + " " + d.substring(2, 7) + "-" + d.substring(7, 11);
        } else if (len == 10 && !d.startsWith("1")) {
            return "+55 " + d.substring(0, 2) + " " + d.substring(2, 6) + "-" + d.substring(6, 10);
        }

        // 2. North America (+1) - US/Canada
        if (d.startsWith("1") && len == 11) {
            return "+1 " + d.substring(1, 4) + " " + d.substring(4, 7) + "-" + d.substring(7, 11);
        } else if (len == 10 && d.startsWith("1")) {
            return "+1 " + d.substring(1, 4) + " " + d.substring(4, 7) + "-" + d.substring(7, 10);
        }

        // 3. United Kingdom (+44)
        if (d.startsWith("44") && len >= 11 && len <= 13) {
            return "+44 " + d.substring(2, 6) + " " + d.substring(6);
        }

        // 4. Portugal (+351)
        if (d.startsWith("351") && len == 12) {
            return "+351 " + d.substring(3, 6) + " " + d.substring(6, 9) + " " + d.substring(9, 12);
        }

        // WhatsApp internal LIDs (e.g. 14+ digits or containing @lid)
        if (raw.indexOf("@lid") >= 0 || (len >= 14 && (d.startsWith("2") || d.startsWith("1")))) {
            return "Participante";
        }

        // Fallback for valid international numbers (prefix with '+')
        if (len >= 7 && len <= 13) {
            return "+" + d;
        }

        return raw;
    }
}
