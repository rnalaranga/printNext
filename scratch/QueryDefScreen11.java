import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.CookieManager;
import java.net.CookiePolicy;

public class QueryDefScreen11 {
    public static void main(String[] args) throws Exception {
        CookieManager cm = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        HttpClient client = HttpClient.newBuilder().cookieHandler(cm).build();
        
        HttpRequest loginReq = HttpRequest.newBuilder()
                .uri(URI.create("http://192.168.1.11/contentwebserver"))
                .header("Content-Type", "text/plain; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "<DeviceInformationModel><Command><Login><userType>Admin</userType><userName>admin</userName><password>123456</password></Login></Command></DeviceInformationModel>"))
                .build();
        HttpResponse<String> loginResp = client.send(loginReq, HttpResponse.BodyHandlers.ofString());
        String cookie = cm.getCookieStore().getCookies().stream()
                .filter(c -> "csrfpId".equalsIgnoreCase(c.getName()))
                .map(c -> c.getValue())
                .findFirst().orElse("");
                
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

        long start = System.currentTimeMillis();
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://192.168.1.11/contentwebserver"))
                .header("Content-Type", "text/xml; charset=UTF-8")
                .header("csrfpId", cookie)
                .header("Referer", "http://192.168.1.11/Administration/Setup/General.html")
                .POST(HttpRequest.BodyPublishers.ofString(setUiXml))
                .build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        long end = System.currentTimeMillis();
        System.out.println("Set DefaultScreen to Copy: " + resp.statusCode() + " in " + (end - start) + "ms. Body: " + resp.body());
    }
}
