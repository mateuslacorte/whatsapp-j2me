package com.whatsapp.util;

import java.util.Vector;

/**
 * Lightweight JSON parser and serializer for CLDC 1.1 / J2ME.
 * Parses JSON Objects and Arrays without reflection or external dependencies.
 * Correctly decodes Unicode escape sequences (backslash-u) and fixes any mojibake for Portuguese accents.
 */
public class SimpleJson {

    private SimpleJson() {}

    public static class JsonObject {
        private Vector keys = new Vector();
        private Vector values = new Vector();

        public void put(String key, Object val) {
            keys.addElement(key);
            values.addElement(val);
        }

        public void put(String key, int val) {
            put(key, new Integer(val));
        }

        public void put(String key, boolean val) {
            put(key, new Boolean(val));
        }

        public Object get(String key) {
            for (int i = 0; i < keys.size(); i++) {
                if (key.equals(keys.elementAt(i))) {
                    return values.elementAt(i);
                }
            }
            return null;
        }

        public String getString(String key) {
            Object o = get(key);
            return o != null ? o.toString() : null;
        }

        public int getInt(String key, int defaultVal) {
            Object o = get(key);
            if (o instanceof Integer) {
                return ((Integer) o).intValue();
            }
            if (o != null) {
                try {
                    return Integer.parseInt(o.toString());
                } catch (Exception ignored) {}
            }
            return defaultVal;
        }

        public long getLong(String key, long defaultVal) {
            Object o = get(key);
            if (o instanceof Long) {
                return ((Long) o).longValue();
            }
            if (o instanceof Integer) {
                return ((Integer) o).longValue();
            }
            if (o != null) {
                try {
                    return Long.parseLong(o.toString());
                } catch (Exception ignored) {}
            }
            return defaultVal;
        }

        public boolean getBoolean(String key, boolean defaultVal) {
            Object o = get(key);
            if (o instanceof Boolean) {
                return ((Boolean) o).booleanValue();
            }
            if (o != null) {
                return "true".equals(o.toString());
            }
            return defaultVal;
        }

        public JsonObject getObject(String key) {
            Object o = get(key);
            return o instanceof JsonObject ? (JsonObject) o : null;
        }

        public JsonArray getArray(String key) {
            Object o = get(key);
            return o instanceof JsonArray ? (JsonArray) o : null;
        }

        public Vector getKeys() {
            return keys;
        }

        public String toString() {
            StringBuffer sb = new StringBuffer("{");
            for (int i = 0; i < keys.size(); i++) {
                if (i > 0) sb.append(',');
                sb.append('"').append(escape(keys.elementAt(i).toString())).append("\":");
                Object v = values.elementAt(i);
                sb.append(formatValue(v));
            }
            sb.append('}');
            return sb.toString();
        }
    }

    public static class JsonArray {
        private Vector elements = new Vector();

        public void add(Object val) {
            elements.addElement(val);
        }

        public int size() {
            return elements.size();
        }

        public Object get(int index) {
            return elements.elementAt(index);
        }

        public JsonObject getObject(int index) {
            Object o = elements.elementAt(index);
            return o instanceof JsonObject ? (JsonObject) o : null;
        }

        public String toString() {
            StringBuffer sb = new StringBuffer("[");
            for (int i = 0; i < elements.size(); i++) {
                if (i > 0) sb.append(',');
                sb.append(formatValue(elements.elementAt(i)));
            }
            sb.append(']');
            return sb.toString();
        }
    }

    private static String formatValue(Object v) {
        if (v == null) return "null";
        if (v instanceof String) {
            return "\"" + escape((String) v) + "\"";
        }
        if (v instanceof JsonObject || v instanceof JsonArray) {
            return v.toString();
        }
        return v.toString();
    }

    private static String escape(String s) {
        StringBuffer sb = new StringBuffer();
        int len = s.length();
        for (int i = 0; i < len; i++) {
            char c = s.charAt(i);
            if (c == '"') sb.append("\\\"");
            else if (c == '\\') sb.append("\\\\");
            else if (c == '\b') sb.append("\\b");
            else if (c == '\f') sb.append("\\f");
            else if (c == '\n') sb.append("\\n");
            else if (c == '\r') sb.append("\\r");
            else if (c == '\t') sb.append("\\t");
            else sb.append(c);
        }
        return sb.toString();
    }

    public static String fixMojibake(String s) {
        if (s == null) return "";
        if (s.indexOf('\u00C3') == -1 && s.indexOf('\u00C2') == -1) {
            return s;
        }
        try {
            byte[] bytes = s.getBytes("ISO-8859-1");
            return new String(bytes, "UTF-8");
        } catch (Exception e) {
            return s;
        }
    }

    public static Object parse(String json) {
        if (json == null) return null;
        Parser p = new Parser(json.trim());
        return p.parseValue();
    }

    private static class Parser {
        private String src;
        private int pos = 0;
        private int len;

        Parser(String s) {
            this.src = s;
            this.len = s.length();
        }

        private void skipWhitespace() {
            while (pos < len) {
                char c = src.charAt(pos);
                if (c == ' ' || c == '\t' || c == '\r' || c == '\n') {
                    pos++;
                } else {
                    break;
                }
            }
        }

        Object parseValue() {
            skipWhitespace();
            if (pos >= len) return null;
            char c = src.charAt(pos);
            if (c == '{') return parseObject();
            if (c == '[') return parseArray();
            if (c == '"') return parseString();
            if (c == 't' || c == 'f') return parseBoolean();
            if (c == 'n') return parseNull();
            return parseNumber();
        }

        JsonObject parseObject() {
            pos++; // skip '{'
            JsonObject obj = new JsonObject();
            skipWhitespace();
            if (pos < len && src.charAt(pos) == '}') {
                pos++;
                return obj;
            }
            while (pos < len) {
                skipWhitespace();
                String key = parseString();
                skipWhitespace();
                if (pos < len && src.charAt(pos) == ':') pos++;
                Object val = parseValue();
                obj.put(key, val);
                skipWhitespace();
                if (pos < len && src.charAt(pos) == ',') {
                    pos++;
                } else if (pos < len && src.charAt(pos) == '}') {
                    pos++;
                    break;
                }
            }
            return obj;
        }

        JsonArray parseArray() {
            pos++; // skip '['
            JsonArray arr = new JsonArray();
            skipWhitespace();
            if (pos < len && src.charAt(pos) == ']') {
                pos++;
                return arr;
            }
            while (pos < len) {
                Object val = parseValue();
                arr.add(val);
                skipWhitespace();
                if (pos < len && src.charAt(pos) == ',') {
                    pos++;
                } else if (pos < len && src.charAt(pos) == ']') {
                    pos++;
                    break;
                }
            }
            return arr;
        }

        String parseString() {
            if (pos < len && src.charAt(pos) == '"') pos++;
            StringBuffer sb = new StringBuffer();
            while (pos < len) {
                char c = src.charAt(pos++);
                if (c == '"') break;
                if (c == '\\' && pos < len) {
                    char esc = src.charAt(pos++);
                    if (esc == '"') sb.append('"');
                    else if (esc == '\\') sb.append('\\');
                    else if (esc == '/') sb.append('/');
                    else if (esc == 'b') sb.append('\b');
                    else if (esc == 'f') sb.append('\f');
                    else if (esc == 'n') sb.append('\n');
                    else if (esc == 'r') sb.append('\r');
                    else if (esc == 't') sb.append('\t');
                    else if (esc == 'u' && pos + 4 <= len) {
                        try {
                            String hex = src.substring(pos, pos + 4);
                            int code = Integer.parseInt(hex, 16);
                            sb.append((char) code);
                            pos += 4;
                        } catch (Exception e) {
                            sb.append('u');
                        }
                    } else {
                        sb.append(esc);
                    }
                } else {
                    sb.append(c);
                }
            }
            return fixMojibake(sb.toString());
        }

        Boolean parseBoolean() {
            if (src.startsWith("true", pos)) {
                pos += 4;
                return Boolean.TRUE;
            }
            if (src.startsWith("false", pos)) {
                pos += 5;
                return Boolean.FALSE;
            }
            return Boolean.FALSE;
        }

        Object parseNull() {
            if (src.startsWith("null", pos)) {
                pos += 4;
            }
            return null;
        }

        Object parseNumber() {
            int start = pos;
            if (pos < len && (src.charAt(pos) == '-' || src.charAt(pos) == '+')) pos++;
            while (pos < len && Character.isDigit(src.charAt(pos))) pos++;
            String numStr = src.substring(start, pos);
            try {
                long val = Long.parseLong(numStr);
                if (val >= Integer.MIN_VALUE && val <= Integer.MAX_VALUE) {
                    return new Integer((int) val);
                }
                return new Long(val);
            } catch (Exception e) {
                return numStr;
            }
        }
    }
}
