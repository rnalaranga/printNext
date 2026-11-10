import java.net.*;
import java.net.http.*;
import java.time.Duration;

public class TestPanelCommands11 {
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

        String[] cmds = {
            "<Command><FunctionClear><commandNode>Panel</commandNode></FunctionClear></Command>",
            "<Command><AutoClear><commandNode>Panel</commandNode></AutoClear></Command>",
            "<Command><Reset><commandNode>Panel</commandNode></Reset></Command>",
            "<Command><ChangeScreen><commandNode>Panel</commandNode><Params><screen>Copy</screen></Params></ChangeScreen></Command>",
            "<Command><Notify><commandNode>Authentication/AuthenticationSettings</commandNode></Notify></Command>",
            "<Command><Reload><commandNode>Authentication/AuthenticationSettings</commandNode></Reload></Command>",
            "<Command><Refresh><commandNode>Panel</commandNode></Refresh></Command>",
            "<Command><Update><commandNode>Authentication/AuthenticationSettings</commandNode></Update></Command>"
        };

        for (String cmd : cmds) {
            String xml = "<DeviceInformationModel>" + cmd + "</DeviceInformationModel>";
            try {
                HttpResponse<String> r = client.send(HttpRequest.newBuilder().uri(URI.create("http://" + ip + "/contentwebserver"))
                        .header("Content-Type", "text/plain; charset=utf-8").header("csrfpId", session).POST(HttpRequest.BodyPublishers.ofString(xml)).build(), HttpResponse.BodyHandlers.ofString());
                System.out.println("CMD: " + cmd + " => " + r.statusCode() + " | " + r.body());
            } catch (Exception e) {
                System.out.println("CMD: " + cmd + " => EXCEPTION: " + e.getMessage());
            }
        }
    }
}
