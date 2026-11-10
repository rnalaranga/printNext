import java.net.*;
import java.net.http.*;
import java.time.Duration;

public class TestCws {
    public static void main(String[] args) throws Exception {
        String printerIp = "192.168.1.11";
        CookieManager cookieManager = new CookieManager();
        cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
        HttpClient client = HttpClient.newBuilder()
                .cookieHandler(cookieManager)
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        HttpRequest handshakeReq = HttpRequest.newBuilder()
                .uri(URI.create("http://" + printerIp + "/"))
                .GET().build();
        HttpResponse<String> handshakeResp = client.send(handshakeReq, HttpResponse.BodyHandlers.ofString());
        
        String session = null;
        for (HttpCookie c : cookieManager.getCookieStore().getCookies()) {
            if ("Session".equalsIgnoreCase(c.getName())) session = c.getValue();
        }
        System.out.println("Handshake session: " + session);
        String clientIp = (session != null && session.contains(".")) ? session.substring(0, session.lastIndexOf('.')) : "192.168.1.100";

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
                .uri(URI.create("http://" + printerIp + "/contentwebserver"))
                .header("Content-Type", "text/plain; charset=utf-8")
                .header("csrfpId", session)
                .POST(HttpRequest.BodyPublishers.ofString(loginXml))
                .build();

        HttpResponse<String> loginResp = client.send(loginReq, HttpResponse.BodyHandlers.ofString());
        System.out.println("Login resp (" + loginResp.statusCode() + "): " + loginResp.body().substring(0, Math.min(200, loginResp.body().length())));

        String queryXml = "<DeviceInformationModel><GetValue><path>Authentication/AuthenticationSettings</path></GetValue></DeviceInformationModel>";
        HttpRequest qReq = HttpRequest.newBuilder()
                .uri(URI.create("http://" + printerIp + "/contentwebserver"))
                .header("Content-Type", "text/plain; charset=utf-8")
                .header("csrfpId", session)
                .POST(HttpRequest.BodyPublishers.ofString(queryXml))
                .build();
        HttpResponse<String> qResp = client.send(qReq, HttpResponse.BodyHandlers.ofString());
        System.out.println("Query Auth Settings:\n" + qResp.body());
    }
}
