import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class Test08 {
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
        HttpResponse<String> hr = client.send(handshake, HttpResponse.BodyHandlers.ofString());
        String sessionCookie = "";
        for (HttpCookie c : cm.getCookieStore().getCookies()) {
            if ("Session".equalsIgnoreCase(c.getName())) {
                sessionCookie = c.getValue();
            }
        }
        System.out.println("Handshake Session=" + sessionCookie);

        String clientIp = "192.168.1.100";
        if (sessionCookie.contains(".")) {
            String prefix = sessionCookie.substring(0, sessionCookie.lastIndexOf('.'));
            if (prefix.matches("\\d+\\.\\d+\\.\\d+\\.\\d+")) {
                clientIp = prefix;
            }
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
        HttpResponse<String> loginResp = client.send(loginReq, HttpResponse.BodyHandlers.ofString());
        for (HttpCookie c : cm.getCookieStore().getCookies()) {
            if ("Session".equalsIgnoreCase(c.getName())) {
                sessionCookie = c.getValue();
            }
        }
        System.out.println("Login status=" + loginResp.statusCode() + " ok=" + loginResp.body().contains("Normal") + " authSession=" + sessionCookie);

        // 3. Query UI settings
        String getUi = "<DeviceInformationModel><GetValue><Controller><Settings><UI/></Settings></Controller></GetValue></DeviceInformationModel>";
        HttpRequest uiReq = HttpRequest.newBuilder()
                .uri(URI.create("http://" + ip + "/contentwebserver"))
                .header("Content-Type", "text/xml; charset=UTF-8")
                .header("csrfpId", sessionCookie)
                .POST(HttpRequest.BodyPublishers.ofString(getUi))
                .build();
        HttpResponse<String> uiResp = client.send(uiReq, HttpResponse.BodyHandlers.ofString());
        System.out.println("UI settings on 192.168.1.12:\n" + uiResp.body());

        // 4. Query Authentication settings
        String getAuth = "<DeviceInformationModel><Command><GetSettings><commandNode>Authentication/AuthenticationSettings</commandNode></GetSettings></Command><GetValue><Authentication><AuthenticationSettings/></Authentication></GetValue></DeviceInformationModel>";
        HttpRequest authReq = HttpRequest.newBuilder()
                .uri(URI.create("http://" + ip + "/contentwebserver"))
                .header("Content-Type", "text/xml; charset=UTF-8")
                .header("csrfpId", sessionCookie)
                .POST(HttpRequest.BodyPublishers.ofString(getAuth))
                .build();
        HttpResponse<String> authResp = client.send(authReq, HttpResponse.BodyHandlers.ofString());
        System.out.println("Auth settings on 192.168.1.12:\n" + authResp.body());
    }
}
