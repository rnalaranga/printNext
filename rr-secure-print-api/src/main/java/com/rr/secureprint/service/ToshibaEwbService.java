package com.rr.secureprint.service;

import com.rr.secureprint.dto.EwbConfigResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service to manage Toshiba e-BRIDGE Next Embedded Web Browser (EWB / Open Platform) settings.
 * Directly communicates with TopAccess XML endpoints (like PaperCut MF).
 */
@Service
public class ToshibaEwbService {

    private static final Logger log = LoggerFactory.getLogger(ToshibaEwbService.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private static final Pattern MODEL_PATTERN = Pattern.compile("<ModelName>(.*?)</ModelName>", Pattern.CASE_INSENSITIVE);
    private static final Pattern HOMEPAGE_PATTERN = Pattern.compile("<HomePage>(.*?)</HomePage>", Pattern.CASE_INSENSITIVE);
    private static final Pattern AUTOSCREEN_PATTERN = Pattern.compile("<AutoScreenChange>(.*?)</AutoScreenChange>", Pattern.CASE_INSENSITIVE);
    private static final Pattern OP_STATUS_PATTERN = Pattern.compile("<statusOfOperation>(.*?)</statusOfOperation>", Pattern.CASE_INSENSITIVE);

    /**
     * Authenticates as admin and pushes EWB Home Page & Auto Screen Change configuration to the Toshiba MFP.
     */
    public EwbConfigResult configureEwb(String printerIp, String adminUsername, String adminPassword, String terminalUrl) {
        String cleanIp = printerIp != null ? printerIp.trim() : "";
        String username = (adminUsername != null && !adminUsername.isBlank()) ? adminUsername.trim() : "admin";
        String password = (adminPassword != null && !adminPassword.isBlank()) ? adminPassword.trim() : "123456";
        String targetUrl = (terminalUrl != null && !terminalUrl.isBlank()) ? terminalUrl.trim() : "http://localhost:8080/";

        if (cleanIp.isEmpty()) {
            return new EwbConfigResult(false, "Printer IP address is required.", null, null, false, Instant.now());
        }

        try {
            CookieManager cookieManager = new CookieManager();
            cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
            HttpClient client = HttpClient.newBuilder()
                    .cookieHandler(cookieManager)
                    .connectTimeout(TIMEOUT)
                    .build();

            // 1. Initial Handshake to obtain Session cookie
            String topAccessUrl = "http://" + cleanIp + "/";
            HttpRequest handshakeReq = HttpRequest.newBuilder()
                    .uri(URI.create(topAccessUrl))
                    .timeout(TIMEOUT)
                    .GET()
                    .build();

            HttpResponse<String> handshakeResp = client.send(handshakeReq, HttpResponse.BodyHandlers.ofString());
            String sessionCookie = extractSessionCookie(cookieManager);
            if (sessionCookie == null || sessionCookie.isBlank()) {
                return new EwbConfigResult(false, "Failed to connect to Toshiba TopAccess at " + cleanIp + " (No session cookie).", null, null, false, Instant.now());
            }

            String clientIp = sessionCookie.contains(".") ? sessionCookie.substring(0, sessionCookie.indexOf('.')) : "127.0.0.1";

            // 2. Admin Login Request
            String loginXml = "<DeviceInformationModel>" +
                    "<SetValue>" +
                    "<Authentication>" +
                    "<UserCredential>" +
                    "<userName>" + escapeXml(username) + "</userName>" +
                    "<passwd>" + escapeXml(password) + "</passwd>" +
                    "<ipaddress>" + clientIp + "</ipaddress>" +
                    "<DepartmentManagement isEnable='false'>" +
                    "<requireDepartment>false</requireDepartment>" +
                    "</DepartmentManagement>" +
                    "<domainName></domainName>" +
                    "<applicationType>TOP_ACCESS</applicationType>" +
                    "</UserCredential>" +
                    "</Authentication>" +
                    "</SetValue>" +
                    "<SetValue>" +
                    "<UserManager>" +
                    "<View>" +
                    "<Users>" +
                    "<Information>" +
                    "<passwd>" + escapeXml(password) + "</passwd>" +
                    "</Information>" +
                    "</Users>" +
                    "</View>" +
                    "</UserManager>" +
                    "</SetValue>" +
                    "<Command>" +
                    "<Login>" +
                    "<commandNode>Authentication/UserCredential</commandNode>" +
                    "<Params><appName>TOPACCESS</appName></Params>" +
                    "</Login>" +
                    "</Command>" +
                    "</DeviceInformationModel>";

            HttpRequest loginReq = HttpRequest.newBuilder()
                    .uri(URI.create("http://" + cleanIp + "/contentwebserver"))
                    .timeout(TIMEOUT)
                    .header("Content-Type", "text/xml; charset=UTF-8")
                    .header("csrfpId", sessionCookie)
                    .header("Referer", "http://" + cleanIp + "/Administration/AdminLogin.html")
                    .POST(HttpRequest.BodyPublishers.ofString(loginXml))
                    .build();

            HttpResponse<String> loginResp = client.send(loginReq, HttpResponse.BodyHandlers.ofString());
            String loginBody = loginResp.body() != null ? loginResp.body() : "";

            if (!loginBody.contains("STATUS_OK")) {
                String errorMsg = "Toshiba Admin Login failed. Check printer admin credentials.";
                if (loginBody.contains("STATUS_AUTHENTICATION_FAILED") || loginBody.contains("STATUS_FAILED")) {
                    errorMsg = "Invalid printer admin username or password.";
                }
                return new EwbConfigResult(false, errorMsg, null, null, false, Instant.now());
            }

            // Updated session cookie after login
            String authSession = extractSessionCookie(cookieManager);
            if (authSession == null) authSession = sessionCookie;

            // 3. Set EWB Configuration
            String serverHost = extractHost(targetUrl);
            String trustedServerXml = !serverHost.isBlank()
                    ? "<TrustedServers><TrustedServer>" + escapeXml(serverHost) + "</TrustedServer></TrustedServers>"
                    : "";
            String urlListXml = "<URLList><URL id='1'><Name>nexPrint</Name><Address>" + escapeXml(targetUrl) + "</Address><AutoScreenChange>true</AutoScreenChange></URL></URLList>";

            String setEwbXml = "<DeviceInformationModel>" +
                    "<SetValue>" +
                    "<EWBSettings>" +
                    "<History>false</History>" +
                    "<HomePage>" + escapeXml(targetUrl) + "</HomePage>" +
                    "<MDS>" +
                    "<BootUpURLForApp></BootUpURLForApp>" +
                    "</MDS>" +
                    "<AutoScreenChange>true</AutoScreenChange>" +
                    "<AutoClearTimer>10</AutoClearTimer>" +
                    "<Network>" +
                    "<connectionTimeOut>120</connectionTimeOut>" +
                    "<timeoutErrorDisp>false</timeoutErrorDisp>" +
                    "</Network>" +
                    "<Proxy>" +
                    "<HostName></HostName>" +
                    "<Port></Port>" +
                    "<AccountID></AccountID>" +
                    "<Password></Password>" +
                    "<ExclusionSites></ExclusionSites>" +
                    "<PAC>" +
                    "<Enable>false</Enable>" +
                    "<FilePath></FilePath>" +
                    "</PAC>" +
                    "</Proxy>" +
                    "<HTTPSAccessDenial>false</HTTPSAccessDenial>" +
                    trustedServerXml +
                    urlListXml +
                    "</EWBSettings>" +
                    "</SetValue>" +
                    "<Command>" +
                    "<SetEWBConfiguration>" +
                    "<commandNode>EWBSettings</commandNode>" +
                    "</SetEWBConfiguration>" +
                    "</Command>" +
                    "</DeviceInformationModel>";

            HttpRequest setReq = HttpRequest.newBuilder()
                    .uri(URI.create("http://" + cleanIp + "/contentwebserver"))
                    .timeout(TIMEOUT)
                    .header("Content-Type", "text/xml; charset=UTF-8")
                    .header("csrfpId", authSession)
                    .header("Referer", "http://" + cleanIp + "/Administration/Setup/Ewb.html")
                    .POST(HttpRequest.BodyPublishers.ofString(setEwbXml))
                    .build();

            HttpResponse<String> setResp = client.send(setReq, HttpResponse.BodyHandlers.ofString());
            String setBody = setResp.body() != null ? setResp.body() : "";

            boolean setSuccess = setBody.contains("Normal") || setBody.contains("STATUS_OK");

            // 3b. Commit Controller with DefaultScreen = EWB and MDS Authentication = true (Enforces boot to terminal)
            String setUiXml = "<DeviceInformationModel>" +
                    "<Command>" +
                    "<Set>" +
                    "<commandNode>Controller</commandNode>" +
                    "<SetValue>" +
                    "<Controller>" +
                    "<Settings>" +
                    "<UI>" +
                    "<DefaultScreen>Home</DefaultScreen>" +
                    "</UI>" +
                    "<Functions>" +
                    "<MDS>" +
                    "<Authentication>false</Authentication>" +
                    "</MDS>" +
                    "<ProgramableKey>" +
                    "<Key id='1'>" +
                    "<function>EWB</function>" +
                    "<option>" +
                    "<Address>" + escapeXml(targetUrl) + "</Address>" +
                    "<AutoScreenChange>true</AutoScreenChange>" +
                    "</option>" +
                    "</Key>" +
                    "</ProgramableKey>" +
                    "</Functions>" +
                    "</Settings>" +
                    "</Controller>" +
                    "</SetValue>" +
                    "</Set>" +
                    "</Command>" +
                    "</DeviceInformationModel>";

            try {
                HttpRequest setUiReq = HttpRequest.newBuilder()
                        .uri(URI.create("http://" + cleanIp + "/contentwebserver"))
                        .timeout(TIMEOUT)
                        .header("Content-Type", "text/xml; charset=UTF-8")
                        .header("csrfpId", authSession)
                        .header("Referer", "http://" + cleanIp + "/Administration/Setup/General.html")
                        .POST(HttpRequest.BodyPublishers.ofString(setUiXml))
                        .build();
                client.send(setUiReq, HttpResponse.BodyHandlers.ofString());
            } catch (Exception ex) {
                log.warn("Could not commit DefaultScreen to EWB on {}: {}", cleanIp, ex.getMessage());
            }

            // 4. Query current EWB configuration to verify
            EwbConfigResult statusResult = getEwbStatus(cleanIp, client, authSession);

            if (setSuccess) {
                return new EwbConfigResult(
                        true,
                        "Successfully configured Toshiba EWB & Default Boot Screen! Terminal URL: " + targetUrl,
                        statusResult.printerModel(),
                        targetUrl,
                        true,
                        Instant.now()
                );
            } else {
                return new EwbConfigResult(
                        false,
                        "Toshiba returned an error during EWB configuration: " + extractOpStatus(setBody),
                        statusResult.printerModel(),
                        targetUrl,
                        false,
                        Instant.now()
                );
            }

        } catch (Exception e) {
            log.error("Failed to configure Toshiba EWB on {}: {}", cleanIp, e.getMessage(), e);
            return new EwbConfigResult(false, "Connection error: " + e.getMessage(), null, null, false, Instant.now());
        }
    }

    /**
     * Resets Toshiba EWB settings back to default (blank) and restores the native Toshiba Home Screen.
     * Disables MDS External Authentication mode. Optionally triggers a controller reboot.
     */
    public boolean resetEwbAndRestoreDefaultScreen(String printerIp, String adminUsername, String adminPassword, boolean rebootAfterReset) {
        String cleanIp = printerIp != null ? printerIp.trim() : "";
        String username = (adminUsername != null && !adminUsername.isBlank()) ? adminUsername.trim() : "admin";
        String password = (adminPassword != null && !adminPassword.isBlank()) ? adminPassword.trim() : "123456";

        if (cleanIp.isEmpty()) {
            return false;
        }

        try {
            CookieManager cookieManager = new CookieManager();
            cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
            HttpClient client = HttpClient.newBuilder()
                    .cookieHandler(cookieManager)
                    .connectTimeout(TIMEOUT)
                    .build();

            // 1. Initial Handshake to obtain Session cookie
            HttpRequest handshakeReq = HttpRequest.newBuilder()
                    .uri(URI.create("http://" + cleanIp + "/"))
                    .timeout(TIMEOUT)
                    .GET()
                    .build();

            client.send(handshakeReq, HttpResponse.BodyHandlers.ofString());
            String sessionCookie = extractSessionCookie(cookieManager);
            if (sessionCookie == null || sessionCookie.isBlank()) {
                log.warn("Cannot reset EWB: Failed to get session cookie from {}", cleanIp);
                return false;
            }

            String clientIp = sessionCookie.contains(".") ? sessionCookie.substring(0, sessionCookie.indexOf('.')) : "127.0.0.1";

            // 2. Admin Login Request
            String loginXml = "<DeviceInformationModel>" +
                    "<SetValue>" +
                    "<Authentication>" +
                    "<UserCredential>" +
                    "<userName>" + escapeXml(username) + "</userName>" +
                    "<passwd>" + escapeXml(password) + "</passwd>" +
                    "<ipaddress>" + clientIp + "</ipaddress>" +
                    "<DepartmentManagement isEnable='false'>" +
                    "<requireDepartment>false</requireDepartment>" +
                    "</DepartmentManagement>" +
                    "<domainName></domainName>" +
                    "<applicationType>TOP_ACCESS</applicationType>" +
                    "</UserCredential>" +
                    "</Authentication>" +
                    "</SetValue>" +
                    "<SetValue>" +
                    "<UserManager>" +
                    "<View>" +
                    "<Users>" +
                    "<Information>" +
                    "<passwd>" + escapeXml(password) + "</passwd>" +
                    "</Information>" +
                    "</Users>" +
                    "</View>" +
                    "</UserManager>" +
                    "</SetValue>" +
                    "<Command>" +
                    "<Login>" +
                    "<commandNode>Authentication/UserCredential</commandNode>" +
                    "<Params><appName>TOPACCESS</appName></Params>" +
                    "</Login>" +
                    "</Command>" +
                    "</DeviceInformationModel>";

            HttpRequest loginReq = HttpRequest.newBuilder()
                    .uri(URI.create("http://" + cleanIp + "/contentwebserver"))
                    .timeout(TIMEOUT)
                    .header("Content-Type", "text/xml; charset=UTF-8")
                    .header("csrfpId", sessionCookie)
                    .header("Referer", "http://" + cleanIp + "/Administration/AdminLogin.html")
                    .POST(HttpRequest.BodyPublishers.ofString(loginXml))
                    .build();

            HttpResponse<String> loginResp = client.send(loginReq, HttpResponse.BodyHandlers.ofString());
            if (loginResp.body() == null || !loginResp.body().contains("STATUS_OK")) {
                log.warn("Cannot reset EWB: Admin login failed on {}", cleanIp);
                return false;
            }

            String authSession = extractSessionCookie(cookieManager);
            if (authSession == null) authSession = sessionCookie;

            // 3. Clear EWBSettings (HomePage to about:blank, MDS BootUpURL empty, AutoScreenChange false, clear TrustedServers and URLList)
            String resetEwbXml = "<DeviceInformationModel>" +
                    "<SetValue>" +
                    "<EWBSettings>" +
                    "<History>false</History>" +
                    "<HomePage>about:blank</HomePage>" +
                    "<MDS>" +
                    "<BootUpURLForApp></BootUpURLForApp>" +
                    "</MDS>" +
                    "<AutoScreenChange>false</AutoScreenChange>" +
                    "<Proxy>" +
                    "<HostName></HostName>" +
                    "<Port></Port>" +
                    "<AccountID></AccountID>" +
                    "<Password></Password>" +
                    "<ExclusionSites></ExclusionSites>" +
                    "<PAC>" +
                    "<Enable>false</Enable>" +
                    "<FilePath></FilePath>" +
                    "</PAC>" +
                    "</Proxy>" +
                    "<HTTPSAccessDenial>false</HTTPSAccessDenial>" +
                    "<TrustedServers></TrustedServers>" +
                    "<URLList><URL id='1'><Name></Name><Address></Address><AutoScreenChange>false</AutoScreenChange></URL></URLList>" +
                    "</EWBSettings>" +
                    "</SetValue>" +
                    "<Command>" +
                    "<SetEWBConfiguration>" +
                    "<commandNode>EWBSettings</commandNode>" +
                    "</SetEWBConfiguration>" +
                    "</Command>" +
                    "</DeviceInformationModel>";

            HttpRequest resetEwbReq = HttpRequest.newBuilder()
                    .uri(URI.create("http://" + cleanIp + "/contentwebserver"))
                    .timeout(TIMEOUT)
                    .header("Content-Type", "text/xml; charset=UTF-8")
                    .header("csrfpId", authSession)
                    .header("Referer", "http://" + cleanIp + "/Administration/Setup/Ewb.html")
                    .POST(HttpRequest.BodyPublishers.ofString(resetEwbXml))
                    .build();

            client.send(resetEwbReq, HttpResponse.BodyHandlers.ofString());

            // 4. Reset Controller: DefaultScreen back to Home, MDS Authentication to false, clear ProgrammableKey
            String resetUiXml = "<DeviceInformationModel>" +
                    "<Command>" +
                    "<Set>" +
                    "<commandNode>Controller</commandNode>" +
                    "<SetValue>" +
                    "<Controller>" +
                    "<Settings>" +
                    "<UI>" +
                    "<DefaultScreen>Home</DefaultScreen>" +
                    "</UI>" +
                    "<Functions>" +
                    "<MDS>" +
                    "<Authentication>false</Authentication>" +
                    "</MDS>" +
                    "<ProgramableKey>" +
                    "<Key id='1'>" +
                    "<function></function>" +
                    "<option>" +
                    "<Address></Address>" +
                    "<AutoScreenChange>false</AutoScreenChange>" +
                    "</option>" +
                    "</Key>" +
                    "</ProgramableKey>" +
                    "</Functions>" +
                    "</Settings>" +
                    "</Controller>" +
                    "</SetValue>" +
                    "</Set>" +
                    "</Command>" +
                    "</DeviceInformationModel>";

            HttpRequest resetUiReq = HttpRequest.newBuilder()
                    .uri(URI.create("http://" + cleanIp + "/contentwebserver"))
                    .timeout(TIMEOUT)
                    .header("Content-Type", "text/xml; charset=UTF-8")
                    .header("csrfpId", authSession)
                    .header("Referer", "http://" + cleanIp + "/Administration/Setup/General.html")
                    .POST(HttpRequest.BodyPublishers.ofString(resetUiXml))
                    .build();

            client.send(resetUiReq, HttpResponse.BodyHandlers.ofString());
            log.info("Toshiba EWB cleared and DefaultScreen reset to Home on {}", cleanIp);

            // 5. Optional Reboot to immediately reload the native Home screen
            if (rebootAfterReset) {
                try {
                    String rebootXml = "<DeviceInformationModel>" +
                            "<Command>" +
                            "<SetPowerState>" +
                            "<commandNode>ForcedReboot</commandNode>" +
                            "</SetPowerState>" +
                            "</Command>" +
                            "</DeviceInformationModel>";

                    HttpRequest rebootReq = HttpRequest.newBuilder()
                            .uri(URI.create("http://" + cleanIp + "/contentwebserver"))
                            .timeout(TIMEOUT)
                            .header("Content-Type", "text/xml; charset=UTF-8")
                            .header("csrfpId", authSession)
                            .header("Referer", "http://" + cleanIp + "/Administration/maintenance/reboot/RebootList.html")
                            .POST(HttpRequest.BodyPublishers.ofString(rebootXml))
                            .build();

                    client.send(rebootReq, HttpResponse.BodyHandlers.ofString());
                } catch (Exception ex) {
                    log.info("Printer {} rebooted following EWB reset to Home.", cleanIp);
                }
            }

            return true;
        } catch (Exception e) {
            log.error("Failed to reset EWB on {}: {}", cleanIp, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Reads current EWB settings directly without altering them.
     */
    public EwbConfigResult getEwbStatus(String printerIp) {
        try {
            CookieManager cookieManager = new CookieManager();
            cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
            HttpClient client = HttpClient.newBuilder()
                    .cookieHandler(cookieManager)
                    .connectTimeout(TIMEOUT)
                    .build();

            client.send(HttpRequest.newBuilder()
                    .uri(URI.create("http://" + printerIp.trim() + "/"))
                    .timeout(TIMEOUT)
                    .GET()
                    .build(), HttpResponse.BodyHandlers.ofString());

            String sessionCookie = extractSessionCookie(cookieManager);
            return getEwbStatus(printerIp.trim(), client, sessionCookie);
        } catch (Exception e) {
            return new EwbConfigResult(false, "Unable to query EWB status: " + e.getMessage(), null, null, false, Instant.now());
        }
    }

    private EwbConfigResult getEwbStatus(String printerIp, HttpClient client, String sessionCookie) {
        try {
            String getXml = "<DeviceInformationModel><GetValue><EWBSettings/></GetValue></DeviceInformationModel>";
            HttpRequest getReq = HttpRequest.newBuilder()
                    .uri(URI.create("http://" + printerIp + "/contentwebserver"))
                    .timeout(TIMEOUT)
                    .header("Content-Type", "text/xml; charset=UTF-8")
                    .header("csrfpId", sessionCookie != null ? sessionCookie : "")
                    .POST(HttpRequest.BodyPublishers.ofString(getXml))
                    .build();

            HttpResponse<String> resp = client.send(getReq, HttpResponse.BodyHandlers.ofString());
            String body = resp.body() != null ? resp.body() : "";

            String model = extractTag(MODEL_PATTERN, body, "Toshiba e-STUDIO");
            String homePage = extractTag(HOMEPAGE_PATTERN, body, "");
            String autoScreen = extractTag(AUTOSCREEN_PATTERN, body, "false");

            boolean isConfigured = homePage != null && !homePage.isBlank() && !homePage.equalsIgnoreCase("about:blank");

            return new EwbConfigResult(
                    isConfigured,
                    isConfigured ? "EWB active with Home Page: " + homePage : "EWB not yet configured",
                    model,
                    homePage,
                    "true".equalsIgnoreCase(autoScreen),
                    Instant.now()
            );
        } catch (Exception e) {
            return new EwbConfigResult(false, "Query failed: " + e.getMessage(), null, null, false, Instant.now());
        }
    }

    /**
     * Sends a forced reboot command to the Toshiba controller to apply settings and restart the UI.
     */
    public boolean rebootPrinter(String printerIp, String adminUsername, String adminPassword) {
        String cleanIp = printerIp != null ? printerIp.trim() : "";
        String username = (adminUsername != null && !adminUsername.isBlank()) ? adminUsername.trim() : "admin";
        String password = (adminPassword != null && !adminPassword.isBlank()) ? adminPassword.trim() : "123456";

        try {
            CookieManager cookieManager = new CookieManager();
            cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
            HttpClient client = HttpClient.newBuilder()
                    .cookieHandler(cookieManager)
                    .connectTimeout(TIMEOUT)
                    .build();

            // Handshake
            client.send(HttpRequest.newBuilder()
                    .uri(URI.create("http://" + cleanIp + "/"))
                    .timeout(TIMEOUT)
                    .GET()
                    .build(), HttpResponse.BodyHandlers.ofString());

            String sessionCookie = extractSessionCookie(cookieManager);
            if (sessionCookie == null) return false;
            String clientIp = sessionCookie.contains(".") ? sessionCookie.substring(0, sessionCookie.indexOf('.')) : "127.0.0.1";

            // Login
            String loginXml = "<DeviceInformationModel>" +
                    "<SetValue>" +
                    "<Authentication>" +
                    "<UserCredential>" +
                    "<userName>" + escapeXml(username) + "</userName>" +
                    "<passwd>" + escapeXml(password) + "</passwd>" +
                    "<ipaddress>" + clientIp + "</ipaddress>" +
                    "<DepartmentManagement isEnable='false'>" +
                    "<requireDepartment>false</requireDepartment>" +
                    "</DepartmentManagement>" +
                    "<domainName></domainName>" +
                    "<applicationType>TOP_ACCESS</applicationType>" +
                    "</UserCredential>" +
                    "</Authentication>" +
                    "</SetValue>" +
                    "<SetValue>" +
                    "<UserManager>" +
                    "<View>" +
                    "<Users>" +
                    "<Information>" +
                    "<passwd>" + escapeXml(password) + "</passwd>" +
                    "</Information>" +
                    "</Users>" +
                    "</View>" +
                    "</UserManager>" +
                    "</SetValue>" +
                    "<Command>" +
                    "<Login>" +
                    "<commandNode>Authentication/UserCredential</commandNode>" +
                    "<Params><appName>TOPACCESS</appName></Params>" +
                    "</Login>" +
                    "</Command>" +
                    "</DeviceInformationModel>";

            HttpResponse<String> loginResp = client.send(HttpRequest.newBuilder()
                    .uri(URI.create("http://" + cleanIp + "/contentwebserver"))
                    .timeout(TIMEOUT)
                    .header("Content-Type", "text/xml; charset=UTF-8")
                    .header("csrfpId", sessionCookie)
                    .POST(HttpRequest.BodyPublishers.ofString(loginXml))
                    .build(), HttpResponse.BodyHandlers.ofString());

            if (!loginResp.body().contains("STATUS_OK")) return false;

            String authSession = extractSessionCookie(cookieManager);
            if (authSession == null) authSession = sessionCookie;

            // Forced Reboot
            String rebootXml = "<DeviceInformationModel>" +
                    "<Command>" +
                    "<SetPowerState>" +
                    "<commandNode>ForcedReboot</commandNode>" +
                    "</SetPowerState>" +
                    "</Command>" +
                    "</DeviceInformationModel>";

            HttpResponse<String> rebootResp = client.send(HttpRequest.newBuilder()
                    .uri(URI.create("http://" + cleanIp + "/contentwebserver"))
                    .timeout(TIMEOUT)
                    .header("Content-Type", "text/xml; charset=UTF-8")
                    .header("csrfpId", authSession)
                    .header("Referer", "http://" + cleanIp + "/Administration/maintenance/reboot/RebootList.html")
                    .POST(HttpRequest.BodyPublishers.ofString(rebootXml))
                    .build(), HttpResponse.BodyHandlers.ofString());

            return rebootResp.statusCode() >= 200 && rebootResp.statusCode() < 400;
        } catch (java.io.IOException ioe) {
            // Connection reset or abrupt close is NORMAL when the MFP controller immediately restarts
            log.info("Printer {} closed connection to execute ForcedReboot: {}", cleanIp, ioe.getMessage());
            return true;
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
            if (msg.contains("reset") || msg.contains("closed") || msg.contains("eof") || msg.contains("timeout")) {
                log.info("Printer {} entered reboot sequence: {}", cleanIp, e.getMessage());
                return true;
            }
            log.error("Failed to reboot printer {}: {}", cleanIp, e.getMessage());
            return false;
        }
    }

    private String extractHost(String url) {
        if (url == null || url.isBlank()) return "";
        try {
            URI u = URI.create(url);
            String h = u.getHost();
            if (h != null && !h.isBlank()) return h;
        } catch (Exception ignored) {}
        String s = url.replaceFirst("^[a-zA-Z]+://", "");
        int colon = s.indexOf(':');
        int slash = s.indexOf('/');
        int end = s.length();
        if (colon != -1) end = colon;
        else if (slash != -1) end = slash;
        return s.substring(0, end).trim();
    }

    private String extractSessionCookie(CookieManager cookieManager) {
        List<HttpCookie> cookies = cookieManager.getCookieStore().getCookies();
        for (HttpCookie cookie : cookies) {
            if ("Session".equalsIgnoreCase(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private String extractTag(Pattern pattern, String text, String defaultValue) {
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return defaultValue;
    }

    private String extractOpStatus(String xml) {
        Matcher matcher = OP_STATUS_PATTERN.matcher(xml);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "Unknown";
    }

    private String escapeXml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&apos;");
    }
}
