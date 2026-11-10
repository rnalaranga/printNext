import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.time.Duration;

public class TestClearLog11 {
    public static void main(String[] args) throws Exception {
        String ip = "192.168.1.10";
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
        System.out.println("Login OK: " + loginResp.body().contains("STATUS_OK"));
        
        // Let's test ClearLog for ALL
        String clearXml = "<DeviceInformationModel>" +
                "<SetValue overrideDelta='false'>" +
                "<LogManager><LogOperation><StartTime>0</StartTime><EndTime>0</EndTime>" +
                "<LogTableInfo><LogType>ALL</LogType></LogTableInfo>" +
                "</LogOperation></LogManager>" +
                "</SetValue>" +
                "<Command>" +
                "<ClearLog><commandNode>LogManager</commandNode><Params><logDetails contentType='XPath'>LogManager/LogOperation</logDetails></Params></ClearLog>" +
                "</Command>" +
                "</DeviceInformationModel>";
                
        long t0 = System.currentTimeMillis();
        HttpResponse<String> clearResp = client.send(HttpRequest.newBuilder()
                .uri(URI.create("http://" + ip + "/contentwebserver"))
                .header("Content-Type", "text/plain; charset=utf-8")
                .header("csrfpId", session)
                .POST(HttpRequest.BodyPublishers.ofString(clearXml)).build(), HttpResponse.BodyHandlers.ofString());
        long t1 = System.currentTimeMillis();
        System.out.println("Clear PRINT_LOG: " + (t1 - t0) + "ms -> " + clearResp.body());

        String retrieveXml = "<DeviceInformationModel>" +
                "<Command><RetrieveLog><commandNode>Log</commandNode><Params><Order>Descending</Order></Params></RetrieveLog></Command>" +
                "<GetValue><Log><JobLog pageSize='20' pageNo='1'><PrintLogs><LogRecord><Common><RecordNumber/><UserName/><CompletedTime/></Common><Print><DocumentName/></Print></LogRecord></PrintLogs></JobLog></Log></GetValue>" +
                "</DeviceInformationModel>";
        HttpResponse<String> retResp = client.send(HttpRequest.newBuilder()
                .uri(URI.create("http://" + ip + "/contentwebserver"))
                .header("Content-Type", "text/plain; charset=utf-8")
                .header("csrfpId", session)
                .POST(HttpRequest.BodyPublishers.ofString(retrieveXml)).build(), HttpResponse.BodyHandlers.ofString());
        System.out.println("Retrieved Logs: " + retResp.body());
    }
}
