import java.net.*;
import java.net.http.*;
import java.time.Duration;

public class TestAuthUpdate {
    public static void main(String[] args) throws Exception {
        String ip = "192.168.1.11";
        CookieManager cookieManager = new CookieManager();
        cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
        HttpClient client = HttpClient.newBuilder()
                .cookieHandler(cookieManager)
                .connectTimeout(Duration.ofSeconds(8))
                .build();

        client.send(HttpRequest.newBuilder().uri(URI.create("http://" + ip + "/")).GET().build(), HttpResponse.BodyHandlers.ofString());

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

        client.send(HttpRequest.newBuilder().uri(URI.create("http://" + ip + "/contentwebserver"))
                .header("Content-Type", "text/plain; charset=utf-8").header("csrfpId", session).POST(HttpRequest.BodyPublishers.ofString(loginXml)).build(), HttpResponse.BodyHandlers.ofString());

        String setXml = "<DeviceInformationModel>" +
                "<SetValue overrideDelta='false'>" +
                "<Payload><path>Authentication/AuthenticationSettings/AuthenticationType/primaryLdapID</path><value>0</value></Payload>" +
                "<Payload><path>Authentication/AuthenticationSettings/CardAuthentication/primaryCardServerID</path><value>0</value></Payload>" +
                "<Payload><path>Authentication/AuthenticationSettings/CardAuthentication/autoRegistrationForCardAuth</path><value>false</value></Payload>" +
                "</SetValue>" +
                "<Command>" +
                "<AuthSettings><commandNode>Authentication/AuthenticationSettings</commandNode></AuthSettings>" +
                "</Command>" +
                "</DeviceInformationModel>";

        HttpResponse<String> setResp = client.send(HttpRequest.newBuilder().uri(URI.create("http://" + ip + "/contentwebserver"))
                .header("Content-Type", "text/plain; charset=utf-8").header("csrfpId", session).POST(HttpRequest.BodyPublishers.ofString(setXml)).build(), HttpResponse.BodyHandlers.ofString());
        System.out.println("Set Response: " + setResp.body());

        String qXml = "<DeviceInformationModel><Command><GetSettings><commandNode>Authentication/AuthenticationSettings</commandNode></GetSettings></Command><GetValue><Authentication><AuthenticationSettings></AuthenticationSettings></Authentication></GetValue></DeviceInformationModel>";
        HttpResponse<String> qResp = client.send(HttpRequest.newBuilder().uri(URI.create("http://" + ip + "/contentwebserver"))
                .header("Content-Type", "text/plain; charset=utf-8").header("csrfpId", session).POST(HttpRequest.BodyPublishers.ofString(qXml)).build(), HttpResponse.BodyHandlers.ofString());
        
        String b = qResp.body();
        System.out.println("primaryLdapID in response: " + (b.contains("<primaryLdapID>0</primaryLdapID>") ? "0 (SUCCESS)" : "NOT 0"));
        System.out.println("primaryCardServerID in response: " + (b.contains("<primaryCardServerID>0</primaryCardServerID>") ? "0 (SUCCESS)" : "NOT 0"));
        System.out.println("autoRegistrationForCardAuth in response: " + (b.contains("<autoRegistrationForCardAuth>false</autoRegistrationForCardAuth>") ? "false (SUCCESS)" : "NOT false"));
    }
}
