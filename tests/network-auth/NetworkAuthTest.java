import com.whatsapp.net.NetworkUtil;
import com.whatsapp.storage.AccountStore;
import javax.microedition.io.Connector;

public class NetworkAuthTest {
    private static final String EXPECTED = "Basic QWxhZGRpbjpvcGVuIHNlc2FtZQ==";
    private static Connector.Request last() {
        return (Connector.Request) Connector.connections.lastElement();
    }
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        boolean bb = args.length > 0;
        System.setProperty("microedition.platform", bb ? "BlackBerry" : "Nokia");
        String base = AccountStore.base;
        NetworkUtil.openHttp(base + "/api/device/test/send");
        check(last().headers.get("Authorization") == null, "Empty credentials must omit Authorization");
        AccountStore.BRIDGE_USERNAME = "Aladdin";
        AccountStore.BRIDGE_PASSWORD = "open sesame";
        NetworkUtil.openHttp(base);
        check(EXPECTED.equals(last().headers.get("Authorization")), "Base URL authorization before transport suffix");
        String[] paths = {"qr.png", "status", "chats", "messages", "sync", "send", "send_audio", "mark_read"};
        Connector.requireAuth = true;
        for (int i = 0; i < paths.length; i++) {
            NetworkUtil.openAndConnect(base + "/api/device/test/" + paths[i], "GET", "test");
            check(EXPECTED.equals(last().headers.get("Authorization")), "Missing GET authorization: " + paths[i]);
            NetworkUtil.openHttp(base + "/api/device/test/" + paths[i]).setRequestMethod("POST");
            check(EXPECTED.equals(last().headers.get("Authorization")), "Missing POST authorization");
        }
        Connector.requireAuth = false;
        String[] outside = {"https://elsewhere.example/api", "https://bridge.example.com.evil/server/whatsapp/api", base + "-other/api", "http://bridge.example.com/server/whatsapp/api"};
        for (int i = 0; i < outside.length; i++) {
            NetworkUtil.openAndConnect(outside[i], "GET", null);
            check(last().headers.get("Authorization") == null, "Credentials leaked outside bridge scope");
        }
        if (bb) {
            Connector.requireAuth = true;
            Connector.failures = 1;
            int before = Connector.connections.size();
            NetworkUtil.openAndConnect(base + "/api/device/test/sync", "GET", null);
            check(Connector.connections.size() == before + 2, "Expected transport retry");
            Connector.Request failed = (Connector.Request) Connector.connections.elementAt(before);
            check(failed.closed && EXPECTED.equals(failed.headers.get("Authorization")), "Retry must close and authorize failed request");
            check(EXPECTED.equals(last().headers.get("Authorization")), "Retry missing authorization");
        }
        AccountStore.base = "https://bridge.example.com/";
        NetworkUtil.openHttp(AccountStore.base + "api/device/test/status");
        check(EXPECTED.equals(last().headers.get("Authorization")), "Root API path authorization");
        AccountStore.BRIDGE_PASSWORD = "";
        try {
            NetworkUtil.openHttp(AccountStore.base + "api/device/test/status");
            throw new AssertionError("Incomplete credentials accepted");
        } catch (java.io.IOException expected) {
            check(last().closed, "Invalid credential connection was not closed");
        }
        System.out.println("PASS: " + (bb ? "BlackBerry" : "standard MIDP") + " authorization, URL scope, GET/POST, optional credentials and cleanup");
    }
}
