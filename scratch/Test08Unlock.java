import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class Test08Unlock {
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
            if ("Session".equalsIgnoreCase(c.getName())) sessionCookie = c.getValue();
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

        // 3. Test Unlock AuthenticatedTarget (copy=false, print=false)
        long start = System.currentTimeMillis();
        String unlockXml = "<DeviceInformationModel>" +
                "<SetValue overrideDelta='false'>" +
                "<Payload><path>Authentication/AuthenticationSettings/openAccess</path><value>1</value></Payload>" +
                "<Payload><path>Authentication/AuthenticationSettings/functionwiseAuthentication</path><value>true</value></Payload>" +
                "<Payload><path>Authentication/AuthenticationSettings/AuthenticationType/primaryLdapID</path><value>0</value></Payload>" +
                "<Payload><path>Authentication/AuthenticationSettings/CardAuthentication/primaryCardServerID</path><value>0</value></Payload>" +
                "<Payload><path>Authentication/AuthenticationSettings/CardAuthentication/autoRegistrationForCardAuth</path><value>false</value></Payload>" +
                "<Payload><path>Authentication/AuthenticationSettings/AuthenticatedTarget/copy</path><value>false</value></Payload>" +
                "<Payload><path>Authentication/AuthenticationSettings/AuthenticatedTarget/scan</path><value>false</value></Payload>" +
                "<Payload><path>Authentication/AuthenticationSettings/AuthenticatedTarget/fax</path><value>false</value></Payload>" +
                "<Payload><path>Authentication/AuthenticationSettings/AuthenticatedTarget/print</path><value>false</value></Payload>" +
                "<Payload><path>Authentication/AuthenticationSettings/AuthenticatedTarget/list</path><value>false</value></Payload>" +
                "<Payload><path>Authentication/AuthenticationSettings/AuthenticatedTarget/userFunctions</path><value>false</value></Payload>" +
                "<Payload><path>Authentication/AuthenticationSettings/AuthenticatedTarget/EWB</path><value>false</value></Payload>" +
                "</SetValue>" +
                "<Command>" +
                "<AuthSettings><commandNode>Authentication/AuthenticationSettings</commandNode></AuthSettings>" +
                "</Command>" +
                "</DeviceInformationModel>";

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://" + ip + "/contentwebserver"))
                .header("Content-Type", "text/plain; charset=utf-8")
                .header("csrfpId", sessionCookie)
                .POST(HttpRequest.BodyPublishers.ofString(unlockXml))
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        long elapsed = System.currentTimeMillis() - start;
        System.out.println("Unlock took " + elapsed + "ms -> " + resp.body().trim());
    }
}
