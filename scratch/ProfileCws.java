import java.net.*;
import java.net.http.*;
import java.time.Duration;

public class ProfileCws {
    public static void main(String[] args) throws Exception {
        String ip = "192.168.1.11";
        CookieManager cookieManager = new CookieManager();
        cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
        HttpClient client = HttpClient.newBuilder()
                .cookieHandler(cookieManager)
                .connectTimeout(Duration.ofSeconds(8))
                .build();

        long t0 = System.currentTimeMillis();
        client.send(HttpRequest.newBuilder().uri(URI.create("http://" + ip + "/")).GET().build(), HttpResponse.BodyHandlers.ofString());
        long tHandshake = System.currentTimeMillis() - t0;

        String session = null;
        for (HttpCookie c : cookieManager.getCookieStore().getCookies()) {
            if ("Session".equalsIgnoreCase(c.getName())) session = c.getValue();
        }
        String clientIp = (session != null && session.contains(".")) ? session.substring(0, session.lastIndexOf('.')) : "192.168.1.100";

        String loginXml = "<DeviceInformationModel>" +
                "<SetValue><Authentication><UserCredential>" +
                "<userName>admin</userName><passwd>123456</passwd><ipaddress>" + clientIp + "</ipaddress>" +
                "<DepartmentManagement isEnable='false'><requireDepartment>false</requireDepartment></DepartmentManagement>" +
                "<domainName></domainName><applicationType>TOP_ACCESS</applicationType>" +
                "</UserCredential></Authentication></SetValue>" +
                "<Command><Login><commandNode>Authentication/UserCredential</commandNode><Params><appName>TOPACCESS</appName></Params></Login></Command>" +
                "<SaveSessionInformation><SessionInfo><Information><type>LoginPassword</type><data>123456</data></Information><Information><type>LoginUser</type><data>admin</data></Information></SessionInfo></SaveSessionInformation>" +
                "<GetValue><Authentication><UserCredential></UserCredential></Authentication></GetValue>" +
                "</DeviceInformationModel>";

        t0 = System.currentTimeMillis();
        client.send(HttpRequest.newBuilder().uri(URI.create("http://" + ip + "/contentwebserver"))
                .header("Content-Type", "text/plain; charset=utf-8").header("csrfpId", session).POST(HttpRequest.BodyPublishers.ofString(loginXml)).build(), HttpResponse.BodyHandlers.ofString());
        long tLogin = System.currentTimeMillis() - t0;
        
        for (HttpCookie c : cookieManager.getCookieStore().getCookies()) {
            if ("Session".equalsIgnoreCase(c.getName())) session = c.getValue();
        }

        t0 = System.currentTimeMillis();
        String unlockXml = "<DeviceInformationModel>" +
                "<SetValue overrideDelta='false'>" +
                "<Payload><path>Authentication/AuthenticationSettings/AuthenticatedTarget/copy</path><value>false</value></Payload>" +
                "<Payload><path>Authentication/AuthenticationSettings/AuthenticatedTarget/scan</path><value>false</value></Payload>" +
                "</SetValue>" +
                "<Command>" +
                "<AuthSettings><commandNode>Authentication/AuthenticationSettings</commandNode></AuthSettings>" +
                "</Command>" +
                "</DeviceInformationModel>";

        HttpResponse<String> r1 = client.send(HttpRequest.newBuilder().uri(URI.create("http://" + ip + "/contentwebserver"))
                .header("Content-Type", "text/plain; charset=utf-8").header("csrfpId", session).POST(HttpRequest.BodyPublishers.ofString(unlockXml)).build(), HttpResponse.BodyHandlers.ofString());
        long tUnlock = System.currentTimeMillis() - t0;

        t0 = System.currentTimeMillis();
        String uiXml = "<DeviceInformationModel>" +
                "<Command>" +
                "<Set>" +
                "<commandNode>Controller</commandNode>" +
                "<SetValue>" +
                "<Controller><Settings><UI><DefaultScreen>Copy</DefaultScreen></UI></Settings></Controller>" +
                "</SetValue>" +
                "</Set>" +
                "</Command>" +
                "</DeviceInformationModel>";

        HttpResponse<String> r2 = client.send(HttpRequest.newBuilder().uri(URI.create("http://" + ip + "/contentwebserver"))
                .header("Content-Type", "text/xml; charset=UTF-8").header("csrfpId", session).POST(HttpRequest.BodyPublishers.ofString(uiXml)).build(), HttpResponse.BodyHandlers.ofString());
        long tUi = System.currentTimeMillis() - t0;

        System.out.println("Handshake: " + tHandshake + "ms");
        System.out.println("Login:     " + tLogin + "ms");
        System.out.println("Unlock:    " + tUnlock + "ms");
        System.out.println("UI Set:    " + tUi + "ms");
        System.out.println("Total:     " + (tHandshake + tLogin + tUnlock + tUi) + "ms");
    }
}
