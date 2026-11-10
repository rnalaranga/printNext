import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.time.Duration;

public class TestPrintLock {
    public static void main(String[] args) throws Exception {
        String ip = "192.168.1.11";
        CookieManager cm = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        HttpClient client = HttpClient.newBuilder().cookieHandler(cm).connectTimeout(Duration.ofSeconds(10)).build();
        
        HttpRequest handshake = HttpRequest.newBuilder().uri(URI.create("http://" + ip + "/")).GET().build();
        client.send(handshake, HttpResponse.BodyHandlers.ofString());
        
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
        System.out.println("Login: " + loginResp.body().contains("STATUS_OK"));
        
        // Let's get current AuthenticatedTarget
        // Let's set print = true in AuthenticatedTarget
        String setXml = "<DeviceInformationModel>" +
                "<SetValue overrideDelta='false'>" +
                "<Payload><path>Authentication/AuthenticationSettings/openAccess</path><value>1</value></Payload>" +
                "<Payload><path>Authentication/AuthenticationSettings/functionwiseAuthentication</path><value>true</value></Payload>" +
                "<Payload><path>Authentication/AuthenticationSettings/allowGuestUser</path><value>false</value></Payload>" +
                "<Payload><path>Authentication/AuthenticationSettings/requireTrustedUser</path><value>true</value></Payload>" +
                "<Payload><path>Authentication/AuthenticationSettings/AuthenticatedTarget/copy</path><value>true</value></Payload>" +
                "<Payload><path>Authentication/AuthenticationSettings/AuthenticatedTarget/scan</path><value>true</value></Payload>" +
                "<Payload><path>Authentication/AuthenticationSettings/AuthenticatedTarget/fax</path><value>true</value></Payload>" +
                "<Payload><path>Authentication/AuthenticationSettings/AuthenticatedTarget/print</path><value>true</value></Payload>" +
                "<Payload><path>Authentication/AuthenticationSettings/AuthenticatedTarget/list</path><value>true</value></Payload>" +
                "<Payload><path>Authentication/AuthenticationSettings/AuthenticatedTarget/userFunctions</path><value>true</value></Payload>" +
                "<Payload><path>Authentication/AuthenticationSettings/AuthenticatedTarget/EWB</path><value>false</value></Payload>" +
                "</SetValue>" +
                "<Command>" +
                "<AuthSettings><commandNode>Authentication/AuthenticationSettings</commandNode></AuthSettings>" +
                "</Command>" +
                "</DeviceInformationModel>";
                
        HttpResponse<String> setResp = client.send(HttpRequest.newBuilder()
                .uri(URI.create("http://" + ip + "/contentwebserver"))
                .header("Content-Type", "text/plain; charset=utf-8")
                .header("csrfpId", session)
                .POST(HttpRequest.BodyPublishers.ofString(setXml)).build(), HttpResponse.BodyHandlers.ofString());
        System.out.println("Set AuthSettings (print=true): " + setResp.body());

        String getXml = "<DeviceInformationModel><Command><GetSettings><commandNode>Authentication/AuthenticationSettings</commandNode></GetSettings></Command><GetValue><Authentication><AuthenticationSettings/></Authentication></GetValue></DeviceInformationModel>";
        HttpResponse<String> getResp = client.send(HttpRequest.newBuilder()
                .uri(URI.create("http://" + ip + "/contentwebserver"))
                .header("Content-Type", "text/plain; charset=utf-8")
                .header("csrfpId", session)
                .POST(HttpRequest.BodyPublishers.ofString(getXml)).build(), HttpResponse.BodyHandlers.ofString());
        int targetIdx = getResp.body().indexOf("<AuthenticatedTarget>");
        if (targetIdx != -1) {
            System.out.println("Result: " + getResp.body().substring(targetIdx, targetIdx + 150));
        }
    }
}
