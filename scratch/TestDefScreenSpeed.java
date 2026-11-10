import java.net.*;
import java.net.http.*;
import java.time.Duration;

public class TestDefScreenSpeed {
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
        
        for (HttpCookie c : cookieManager.getCookieStore().getCookies()) {
            if ("Session".equalsIgnoreCase(c.getName())) session = c.getValue();
        }

        long t0 = System.currentTimeMillis();
        String setUiXml = "<DeviceInformationModel>" +
                "<Command>" +
                "<Set>" +
                "<commandNode>Controller</commandNode>" +
                "<SetValue>" +
                "<Controller>" +
                "<Settings>" +
                "<UI>" +
                "<DefaultScreen>Copy</DefaultScreen>" +
                "</UI>" +
                "</Settings>" +
                "</Controller>" +
                "</SetValue>" +
                "</Set>" +
                "</Command>" +
                "</DeviceInformationModel>";

        HttpResponse<String> r = client.send(HttpRequest.newBuilder().uri(URI.create("http://" + ip + "/contentwebserver"))
                .header("Content-Type", "text/xml; charset=UTF-8").header("csrfpId", session).POST(HttpRequest.BodyPublishers.ofString(setUiXml)).build(), HttpResponse.BodyHandlers.ofString());
        long dt = System.currentTimeMillis() - t0;
        System.out.println("Set DefaultScreen to Copy in " + dt + "ms, status=" + r.statusCode() + " body=" + r.body());

        // Now set it back to EWB
        t0 = System.currentTimeMillis();
        String setEwbXml = setUiXml.replace("Copy", "EWB");
        HttpResponse<String> r2 = client.send(HttpRequest.newBuilder().uri(URI.create("http://" + ip + "/contentwebserver"))
                .header("Content-Type", "text/xml; charset=UTF-8").header("csrfpId", session).POST(HttpRequest.BodyPublishers.ofString(setEwbXml)).build(), HttpResponse.BodyHandlers.ofString());
        dt = System.currentTimeMillis() - t0;
        System.out.println("Set DefaultScreen to EWB in " + dt + "ms, status=" + r2.statusCode() + " body=" + r2.body());
    }
}
