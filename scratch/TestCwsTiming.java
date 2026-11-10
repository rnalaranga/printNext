import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class TestCwsTiming {
    public static void main(String[] args) throws Exception {
        CookieManager cm = new CookieManager();
        cm.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
        HttpClient client = HttpClient.newBuilder()
                .cookieHandler(cm)
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        long t0 = System.currentTimeMillis();
        HttpRequest r1 = HttpRequest.newBuilder()
                .uri(URI.create("http://192.168.1.11/"))
                .timeout(Duration.ofSeconds(5))
                .GET().build();
        HttpResponse<String> resp1 = client.send(r1, HttpResponse.BodyHandlers.ofString());
        System.out.println("Handshake GET: " + (System.currentTimeMillis() - t0) + "ms, status: " + resp1.statusCode());

        String sessionCookie = null;
        for (String sc : resp1.headers().allValues("Set-Cookie")) {
            if (sc.startsWith("Session=")) {
                int end = sc.indexOf(';');
                sessionCookie = (end != -1) ? sc.substring("Session=".length(), end).trim() : sc.substring("Session=".length()).trim();
            }
        }
        System.out.println("Session cookie: " + sessionCookie);

        String clientIp = sessionCookie.substring(0, sessionCookie.lastIndexOf('.'));
        String loginXml = "<DeviceInformationModel><SetValue><Authentication><UserCredential><userName>admin</userName><passwd>123456</passwd><ipaddress>" + clientIp + "</ipaddress><DepartmentManagement isEnable='false'><requireDepartment>false</requireDepartment></DepartmentManagement><domainName></domainName><applicationType>TOP_ACCESS</applicationType></UserCredential></Authentication></SetValue><Command><Login><commandNode>Authentication/UserCredential</commandNode><Params><appName>TOPACCESS</appName></Params></Login></Command></DeviceInformationModel>";

        long t1 = System.currentTimeMillis();
        HttpRequest r2 = HttpRequest.newBuilder()
                .uri(URI.create("http://192.168.1.11/contentwebserver"))
                .header("Content-Type", "text/plain; charset=utf-8")
                .header("csrfpId", sessionCookie)
                .timeout(Duration.ofSeconds(5))
                .POST(HttpRequest.BodyPublishers.ofString(loginXml)).build();
        HttpResponse<String> resp2 = client.send(r2, HttpResponse.BodyHandlers.ofString());
        System.out.println("Login POST: " + (System.currentTimeMillis() - t1) + "ms, status: " + resp2.statusCode());

        for (String sc : resp2.headers().allValues("Set-Cookie")) {
            if (sc.startsWith("Session=")) {
                int end = sc.indexOf(';');
                sessionCookie = (end != -1) ? sc.substring("Session=".length(), end).trim() : sc.substring("Session=".length()).trim();
            }
        }

        // Now sleep 5 seconds and send second request on same client
        System.out.println("Sleeping 5 seconds...");
        Thread.sleep(5000);

        String queryXml = "<DeviceInformationModel><Command><GetSettings><commandNode>Authentication/AuthenticationSettings</commandNode></GetSettings></Command><GetValue><Authentication><AuthenticationSettings></AuthenticationSettings></Authentication></GetValue></DeviceInformationModel>";
        long t2 = System.currentTimeMillis();
        try {
            HttpRequest r3 = HttpRequest.newBuilder()
                    .uri(URI.create("http://192.168.1.11/contentwebserver"))
                    .header("Content-Type", "text/plain; charset=utf-8")
                    .header("csrfpId", sessionCookie)
                    .timeout(Duration.ofSeconds(5))
                    .POST(HttpRequest.BodyPublishers.ofString(queryXml)).build();
            HttpResponse<String> resp3 = client.send(r3, HttpResponse.BodyHandlers.ofString());
            System.out.println("Follow-up Query POST: " + (System.currentTimeMillis() - t2) + "ms, status: " + resp3.statusCode());
        } catch (Exception ex) {
            System.out.println("Follow-up Query POST FAILED after " + (System.currentTimeMillis() - t2) + "ms: " + ex.getClass().getName() + " -> " + ex.getMessage());
        }
    }
}
