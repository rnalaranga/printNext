import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class Test08Screen {
    public static void main(String[] args) throws Exception {
        String ip = "192.168.1.12";
        CookieManager cm = new CookieManager();
        HttpClient client = HttpClient.newBuilder()
                .cookieHandler(cm)
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        // 1. Handshake GET
        HttpRequest handshake = HttpRequest.newBuilder()
                .uri(URI.create("http://" + ip + "/FrameIndex.html"))
                .GET().build();
        client.send(handshake, HttpResponse.BodyHandlers.ofString());
        String sessionCookie = "";
        for (HttpCookie c : cm.getCookieStore().getCookies()) {
            if ("Session".equalsIgnoreCase(c.getName())) {
                sessionCookie = c.getValue();
            }
        }

        String clientIp = "192.168.1.100";
        if (sessionCookie.contains(".")) {
            String prefix = sessionCookie.substring(0, sessionCookie.lastIndexOf('.'));
            if (prefix.matches("\\d+\\.\\d+\\.\\d+\\.\\d+")) clientIp = prefix;
        }

        // 2. Login
        String loginXml = "<DeviceInformationModel>" +
                "<SetValue><Authentication><UserCredential>" +
                "<userName>admin</userName>" +
                "<passwd>123456</passwd>" +
                "<ipaddress>" + clientIp + "</ipaddress>" +
                "<DepartmentManagement isEnable='false'>" +
                "<requireDepartment>false</requireDepartment>" +
                "</DepartmentManagement>" +
                "<domainName></domainName>" +
                "<applicationType>TOP_ACCESS</applicationType>" +
                "</UserCredential></Authentication></SetValue>" +
                "<Command><Login><commandNode>Authentication/UserCredential</commandNode><Params><appName>TOPACCESS</appName></Params></Login></Command>" +
                "<SaveSessionInformation><SessionInfo>" +
                "<Information><type>LoginPassword</type><data>123456</data></Information>" +
                "<Information><type>LoginUser</type><data>admin</data></Information>" +
                "</SessionInfo></SaveSessionInformation>" +
                "<GetValue><Authentication><UserCredential></UserCredential></Authentication></GetValue>" +
                "</DeviceInformationModel>";

        HttpRequest loginReq = HttpRequest.newBuilder()
                .uri(URI.create("http://" + ip + "/contentwebserver"))
                .header("Content-Type", "text/plain; charset=utf-8")
                .header("csrfpId", sessionCookie)
                .POST(HttpRequest.BodyPublishers.ofString(loginXml))
                .build();
        client.send(loginReq, HttpResponse.BodyHandlers.ofString());
        for (HttpCookie c : cm.getCookieStore().getCookies()) {
            if ("Session".equalsIgnoreCase(c.getName())) sessionCookie = c.getValue();
        }

        // 3. Test Set DefaultScreen to Copy
        testSetScreen(client, ip, sessionCookie, "Copy");

        // 4. Test Set DefaultScreen to Print
        testSetScreen(client, ip, sessionCookie, "Print");

        // 5. Test Set DefaultScreen to EWB
        testSetScreen(client, ip, sessionCookie, "EWB");
    }

    private static void testSetScreen(HttpClient client, String ip, String sessionCookie, String screen) throws Exception {
        long start = System.currentTimeMillis();
        String progKeyXml = "EWB".equalsIgnoreCase(screen)
                ? "<Functions><ProgramableKey><Key id='1'><function></function><option><Address></Address><AutoScreenChange>false</AutoScreenChange></option></Key></ProgramableKey></Functions>"
                : "";
        String setUiXml = "<DeviceInformationModel>" +
                "<Command>" +
                "<Set>" +
                "<commandNode>Controller</commandNode>" +
                "<SetValue>" +
                "<Controller>" +
                "<Settings>" +
                "<UI>" +
                "<DefaultScreen>" + screen + "</DefaultScreen>" +
                "</UI>" +
                progKeyXml +
                "</Settings>" +
                "</Controller>" +
                "</SetValue>" +
                "</Set>" +
                "</Command>" +
                "</DeviceInformationModel>";

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://" + ip + "/contentwebserver"))
                .header("Content-Type", "text/xml; charset=UTF-8")
                .header("csrfpId", sessionCookie)
                .header("Referer", "http://" + ip + "/Administration/Setup/General.html")
                .POST(HttpRequest.BodyPublishers.ofString(setUiXml))
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        long elapsed = System.currentTimeMillis() - start;
        System.out.println("Set screen=" + screen + " took " + elapsed + "ms -> " + resp.body().trim());
    }
}
