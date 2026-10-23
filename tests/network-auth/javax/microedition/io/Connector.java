package javax.microedition.io;

import java.io.IOException;
import java.lang.reflect.*;
import java.util.Hashtable;
import java.util.Vector;

/** Transport fixture records headers and can fail the first BlackBerry GET. */
public class Connector {
    public static final int READ_WRITE = 3;
    public static Vector connections = new Vector();
    public static int failures = 0;
    public static boolean requireAuth = false;
    public static class Request implements InvocationHandler {
        public Hashtable headers = new Hashtable();
        public boolean closed;
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String name = method.getName();
            if (name.equals("setRequestProperty")) headers.put(args[0], args[1]);
            if (name.equals("getResponseCode")) {
                if (requireAuth && !headers.containsKey("Authorization")) throw new AssertionError("GET connected before Authorization");
                if (failures > 0) { failures--; throw new IOException("Simulated transport failure"); }
                return new Integer(200);
            }
            if (name.equals("close")) closed = true;
            return null;
        }
    }
    public static Connection open(String url, int mode) throws IOException {
        Request request = new Request();
        connections.addElement(request);
        return (Connection) Proxy.newProxyInstance(Connector.class.getClassLoader(), new Class[] {HttpConnection.class}, request);
    }
    public static Connection open(String url, int mode, boolean timeouts) throws IOException { return open(url, mode); }
}
