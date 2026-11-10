import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.time.Duration;

public class TestScreenPrint {
    public static void main(String[] args) throws Exception {
        String ip = "192.168.1.11";
        CookieManager cm = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        HttpClient client = HttpClient.newBuilder().cookieHandler(cm).connectTimeout(Duration.ofSeconds(10)).build();
        
        client.send(HttpRequest.newBuilder().uri(URI.create("http://" + ip + "/")).GET().build(), HttpResponse.BodyHandlers.ofString());
        String session = null;
        for (var c : cm.getCookieStore().getCookies()) {
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
                
        HttpResponse<String> loginResp = client.send(HttpRequest.newBuilder()
                .uri(URI.create("http://" + ip + "/contentwebserver"))
                .header("Content-Type", "text/plain; charset=utf-8")
                .header("csrfpId", session)
                .POST(HttpRequest.BodyPublishers.ofString(loginXml)).build(), HttpResponse.BodyHandlers.ofString());
                
        for (var c : cm.getCookieStore().getCookies()) {
            if ("Session".equalsIgnoreCase(c.getName())) session = c.getValue();
        }
        
        // Test DefaultScreen = Print
        String setXml = "<DeviceInformationModel>" +
                "<Command><Set><commandNode>Controller</commandNode><SetValue><Controller><Settings><UI>" +
                "<DefaultScreen>Print</DefaultScreen>" +
                "</UI></Settings></Controller></SetValue></Set></Command></DeviceInformationModel>";
        HttpResponse<String> setResp = client.send(HttpRequest.newBuilder()
                .uri(URI.create("http://" + ip + "/contentwebserver"))
                .header("Content-Type", "text/plain; charset=utf-8")
                .header("csrfpId", session)
                .POST(HttpRequest.BodyPublishers.ofString(setXml)).build(), HttpResponse.BodyHandlers.ofString());
        System.out.println("Set DefaultScreen=Print: " + setResp.body());

        // Also query DefaultScreen
        String getXml = "<DeviceInformationModel><GetValue><Controller><Settings><UI><DefaultScreen/></UI></Settings></Controller></GetValue></DeviceInformationModel>";
        HttpResponse<String> getResp = client.send(HttpRequest.newBuilder()
                .uri(URI.create("http://" + ip + "/contentwebserver"))
                .header("Content-Type", "text/plain; charset=utf-8")
                .header("csrfpId", session)
                .POST(HttpRequest.BodyPublishers.ofString(getXml)).build(), HttpResponse.BodyHandlers.ofString());
        System.out.println("Current DefaultScreen: " + getResp.body());
    }
}
