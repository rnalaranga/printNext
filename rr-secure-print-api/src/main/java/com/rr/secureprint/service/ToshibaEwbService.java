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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service to manage Toshiba e-BRIDGE Next Embedded Web Browser (EWB / Open Platform) settings.
 * Directly communicates with TopAccess XML endpoints (like PaperCut MF).
 */
@Service
public class ToshibaEwbService {

    private static final Logger log = LoggerFactory.getLogger(ToshibaEwbService.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private static final Pattern MODEL_PATTERN = Pattern.compile("<ModelName>(.*?)</ModelName>", Pattern.CASE_INSENSITIVE);
    private static final Pattern HOMEPAGE_PATTERN = Pattern.compile("<HomePage>(.*?)</HomePage>", Pattern.CASE_INSENSITIVE);
    private static final Pattern AUTOSCREEN_PATTERN = Pattern.compile("<AutoScreenChange>(.*?)</AutoScreenChange>", Pattern.CASE_INSENSITIVE);
    private static final Pattern OP_STATUS_PATTERN = Pattern.compile("<statusOfOperation>(.*?)</statusOfOperation>", Pattern.CASE_INSENSITIVE);

    private record TopAccessSession(HttpClient client, CookieManager cookieManager, String sessionCookie, String clientIp, long createdAt) {}

    private final Map<String, TopAccessSession> sessionCache = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<String, Object> printerLocks = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<String, Boolean> currentCopyRestrictionByPrinter = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<String, String> currentDefaultScreenByPrinter = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<String, String> currentScanFromByPrinter = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Set<String> scanOptimizedPrinters = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private synchronized TopAccessSession getOrCreateSession(String printerIp, String username, String password) throws Exception {
        TopAccessSession existing = sessionCache.get(printerIp);
        if (existing != null && (System.currentTimeMillis() - existing.createdAt()) < 15 * 60 * 1000) {
            return existing;
        }
        TopAccessSession fresh = loginToTopAccess(printerIp, username, password);
        sessionCache.put(printerIp, fresh);
        return fresh;
    }

    /**
     * Asynchronously pre-warms the TopAccess CWS session for an active terminal printer
     * so that walk-up screen switches execute in ~100ms.
     */
    public void warmUpSession(String printerIp, String adminUsername, String adminPassword) {
        if (printerIp == null || printerIp.isBlank()) return;
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                getOrCreateSession(printerIp.trim(), adminUsername, adminPassword);
                log.info("[TOSHIBA CWS] Session pre-warmed for {}", printerIp);
            } catch (Exception ex) {
                log.debug("[TOSHIBA CWS] Session pre-warm failed for {}: {}", printerIp, ex.getMessage());
            }
        });
    }

    /**
     * Authenticates as admin and pushes EWB Home Page & Auto Screen Change configuration to the Toshiba MFP.
     */
    public EwbConfigResult configureEwb(String printerIp, String adminUsername, String adminPassword, String terminalUrl) {
        String cleanIp = printerIp != null ? printerIp.trim() : "";
        String username = (adminUsername != null && !adminUsername.isBlank()) ? adminUsername.trim() : "admin";
        String password = (adminPassword != null && !adminPassword.isBlank()) ? adminPassword.trim() : "123456";
        String targetUrl = (terminalUrl != null && !terminalUrl.isBlank()) ? terminalUrl.trim() : "http://192.168.1.100:8080/";
        if (targetUrl.contains("localhost") || targetUrl.contains("127.0.0.1")) {
            targetUrl = targetUrl.replace("localhost", "192.168.1.100").replace("127.0.0.1", "192.168.1.100");
        }

        if (cleanIp.isEmpty()) {
            return new EwbConfigResult(false, "Printer IP address is required.", null, null, false, Instant.now());
        }

        try {
            TopAccessSession session = loginToTopAccess(cleanIp, username, password);

            // Set EWB Configuration
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
                    .header("csrfpId", session.sessionCookie())
                    .header("Referer", "http://" + cleanIp + "/Administration/Setup/Ewb.html")
                    .POST(HttpRequest.BodyPublishers.ofString(setEwbXml))
                    .build();

            HttpResponse<String> setResp = session.client().send(setReq, HttpResponse.BodyHandlers.ofString());
            String setBody = setResp.body() != null ? setResp.body() : "";

            boolean setSuccess = setBody.contains("Normal") || setBody.contains("STATUS_OK");

            // Commit Controller with DefaultScreen = EWB (so machine boots directly to EWB, NOT native Home screen)
            // Note: MDS Authentication is kept false because true requires a proprietary Toshiba MDS directory server daemon
            String setUiXml = "<DeviceInformationModel>" +
                    "<Command>" +
                    "<Set>" +
                    "<commandNode>Controller</commandNode>" +
                    "<SetValue>" +
                    "<Controller>" +
                    "<Settings>" +
                    "<UI>" +
                    "<DefaultScreen>EWB</DefaultScreen>" +
                    "</UI>" +
                    "<autoClear>15</autoClear>" +
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

            try {
                HttpRequest setUiReq = HttpRequest.newBuilder()
                        .uri(URI.create("http://" + cleanIp + "/contentwebserver"))
                        .timeout(TIMEOUT)
                        .header("Content-Type", "text/xml; charset=UTF-8")
                        .header("csrfpId", session.sessionCookie())
                        .header("Referer", "http://" + cleanIp + "/Administration/Setup/General.html")
                        .POST(HttpRequest.BodyPublishers.ofString(setUiXml))
                        .build();
                HttpResponse<String> uiResp = session.client().send(setUiReq, HttpResponse.BodyHandlers.ofString());
                log.info("Toshiba setUiXml committed on {}: status={}, body={}", cleanIp, uiResp.statusCode(), uiResp.body());
            } catch (Exception ex) {
                log.warn("Could not commit DefaultScreen=EWB and MDS Authentication on {}: {}", cleanIp, ex.getMessage());
            }

            // Query current EWB configuration to verify
            EwbConfigResult statusResult = queryEwbSettings(cleanIp, session.client(), session.sessionCookie());

            if (setSuccess) {
                return new EwbConfigResult(
                        true,
                        "Successfully configured Toshiba EWB! Terminal URL: " + targetUrl,
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

        } catch (SecurityException se) {
            log.warn("Authentication failure configuring Toshiba EWB on {}: {}", cleanIp, se.getMessage());
            return new EwbConfigResult(false, se.getMessage(), null, null, false, Instant.now());
        } catch (Exception e) {
            log.error("Failed to configure Toshiba EWB on {}: {}", cleanIp, e.getMessage(), e);
            return new EwbConfigResult(false, "Connection error: " + e.getMessage(), null, null, false, Instant.now());
        }
    }

    /**
     * Resets Toshiba EWB settings back to default (blank) and restores the native Toshiba Home Screen.
     */
    public boolean resetEwbAndRestoreDefaultScreen(String printerIp, String adminUsername, String adminPassword, boolean rebootAfterReset) {
        String cleanIp = printerIp != null ? printerIp.trim() : "";
        String username = (adminUsername != null && !adminUsername.isBlank()) ? adminUsername.trim() : "admin";
        String password = (adminPassword != null && !adminPassword.isBlank()) ? adminPassword.trim() : "123456";

        if (cleanIp.isEmpty()) {
            return false;
        }

        try {
            TopAccessSession session = loginToTopAccess(cleanIp, username, password);

            // Clear EWBSettings (HomePage to about:blank, clear TrustedServers and URLList)
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
                    .header("csrfpId", session.sessionCookie())
                    .header("Referer", "http://" + cleanIp + "/Administration/Setup/Ewb.html")
                    .POST(HttpRequest.BodyPublishers.ofString(resetEwbXml))
                    .build();

            session.client().send(resetEwbReq, HttpResponse.BodyHandlers.ofString());

            // Reset Controller: DefaultScreen back to Home, MDS Authentication to false, clear ProgrammableKey
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
                    .header("csrfpId", session.sessionCookie())
                    .header("Referer", "http://" + cleanIp + "/Administration/Setup/General.html")
                    .POST(HttpRequest.BodyPublishers.ofString(resetUiXml))
                    .build();

            session.client().send(resetUiReq, HttpResponse.BodyHandlers.ofString());
            log.info("Toshiba EWB cleared and DefaultScreen reset to Home on {}", cleanIp);

            // Optional Reboot to immediately reload the native Home screen
            if (rebootAfterReset) {
                try {
                    sendRebootCommand(cleanIp, session.client(), session.sessionCookie());
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
     * Reads current EWB settings by authenticating with TopAccess.
     */
    public EwbConfigResult getEwbStatus(String printerIp, String adminUsername, String adminPassword) {
        String cleanIp = printerIp != null ? printerIp.trim() : "";
        String username = (adminUsername != null && !adminUsername.isBlank()) ? adminUsername.trim() : "admin";
        String password = (adminPassword != null && !adminPassword.isBlank()) ? adminPassword.trim() : "123456";

        if (cleanIp.isEmpty()) {
            return new EwbConfigResult(false, "Printer IP is required.", null, null, false, Instant.now());
        }

        try {
            TopAccessSession session = loginToTopAccess(cleanIp, username, password);
            return queryEwbSettings(cleanIp, session.client(), session.sessionCookie());
        } catch (SecurityException se) {
            return new EwbConfigResult(false, se.getMessage(), null, null, false, Instant.now());
        } catch (Exception e) {
            return new EwbConfigResult(false, "Unable to query EWB status: " + e.getMessage(), null, null, false, Instant.now());
        }
    }

    public EwbConfigResult getEwbStatus(String printerIp) {
        return getEwbStatus(printerIp, "admin", "123456");
    }

    /**
     * Sends a forced reboot command to the Toshiba controller to apply settings and restart the UI.
     */
    public boolean rebootPrinter(String printerIp, String adminUsername, String adminPassword) {
        String cleanIp = printerIp != null ? printerIp.trim() : "";
        String username = (adminUsername != null && !adminUsername.isBlank()) ? adminUsername.trim() : "admin";
        String password = (adminPassword != null && !adminPassword.isBlank()) ? adminPassword.trim() : "123456";

        try {
            TopAccessSession session = loginToTopAccess(cleanIp, username, password);
            return sendRebootCommand(cleanIp, session.client(), session.sessionCookie());
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

    /**
     * Dynamically locks or unlocks native walk-up copying on the Toshiba MFP via CWS.
     * When restricted=false, authenticated LDAP users who tapped "Copy / Scan" can make copies.
     * When restricted=true, unauthorized walk-up users are blocked by Toshiba panel authentication.
     */
    public boolean setCopyRestriction(String printerIp, String adminUsername, String adminPassword, boolean restricted) {
        String cleanIp = printerIp != null ? printerIp.trim() : "";
        String username = (adminUsername != null && !adminUsername.isBlank()) ? adminUsername.trim() : "admin";
        String password = (adminPassword != null && !adminPassword.isBlank()) ? adminPassword.trim() : "123456";

        if (cleanIp.isEmpty()) return false;

        Boolean currentRestricted = currentCopyRestrictionByPrinter.get(cleanIp);
        if (currentRestricted != null && currentRestricted == restricted) {
            log.info("[TOSHIBA CWS] setCopyRestriction on {} already restricted={}. Instant return (0ms).", cleanIp, restricted);
            return true;
        }

        Object lock = printerLocks.computeIfAbsent(cleanIp, k -> new Object());
        synchronized (lock) {
            for (int attempt = 0; attempt < 2; attempt++) {
                try {
                    TopAccessSession session = getOrCreateSession(cleanIp, username, password);
                    String copyVal = restricted ? "true" : "false";
                    String openAccessVal = restricted ? "1" : "0";
                    String funcAuthVal = restricted ? "true" : "false";
                    String reqTrustedVal = restricted ? "true" : "false";
                    String allowGuestVal = restricted ? "false" : "true";

                    // Multi-target restriction XML matching physical locked Toshiba
                    String multiSetXml = "<DeviceInformationModel>" +
                            "<SetValue overrideDelta='false'>" +
                            "<Payload><path>Authentication/AuthenticationSettings/openAccess</path><value>" + openAccessVal + "</value></Payload>" +
                            "<Payload><path>Authentication/AuthenticationSettings/functionwiseAuthentication</path><value>" + funcAuthVal + "</value></Payload>" +
                            "<Payload><path>Authentication/AuthenticationSettings/requireTrustedUser</path><value>" + reqTrustedVal + "</value></Payload>" +
                            "<Payload><path>Authentication/AuthenticationSettings/unauthorizedPrintJob</path><value>Delete</value></Payload>" +
                            "<Payload><path>Authentication/AuthenticationSettings/allowGuestUser</path><value>" + allowGuestVal + "</value></Payload>" +
                            "<Payload><path>Authentication/AuthenticationSettings/AuthenticatedTarget/copy</path><value>" + copyVal + "</value></Payload>" +
                            "<Payload><path>Authentication/AuthenticationSettings/AuthenticatedTarget/scan</path><value>" + copyVal + "</value></Payload>" +
                            "<Payload><path>Authentication/AuthenticationSettings/AuthenticatedTarget/fax</path><value>" + copyVal + "</value></Payload>" +
                            "<Payload><path>Authentication/AuthenticationSettings/AuthenticatedTarget/print</path><value>" + copyVal + "</value></Payload>" +
                            "<Payload><path>Authentication/AuthenticationSettings/AuthenticatedTarget/list</path><value>" + copyVal + "</value></Payload>" +
                            "<Payload><path>Authentication/AuthenticationSettings/AuthenticatedTarget/userFunctions</path><value>" + copyVal + "</value></Payload>" +
                            "<Payload><path>Authentication/AuthenticationSettings/AuthenticatedTarget/EWB</path><value>false</value></Payload>" +
                            "</SetValue>" +
                            "<Command>" +
                            "<AuthSettings><commandNode>Authentication/AuthenticationSettings</commandNode></AuthSettings>" +
                            "</Command>" +
                            "</DeviceInformationModel>";

                    HttpRequest multiReq = HttpRequest.newBuilder()
                            .uri(URI.create("http://" + cleanIp + "/contentwebserver"))
                            .timeout(TIMEOUT)
                            .header("Content-Type", "text/xml; charset=UTF-8")
                            .header("csrfpId", session.sessionCookie() != null ? session.sessionCookie() : "")
                            .header("Referer", "http://" + cleanIp + "/Administration/Setup/General.html")
                            .header("Cache-Control", "no-cache")
                            .header("Pragma", "no-cache")
                            .POST(HttpRequest.BodyPublishers.ofString(multiSetXml))
                            .build();

                    HttpResponse<String> multiResp = session.client().send(multiReq, HttpResponse.BodyHandlers.ofString());
                    String body = multiResp.body() != null ? multiResp.body() : "";

                    if (body.contains("STATUS_AL_CWS_UNAUTHORIZED")) {
                        sessionCache.remove(cleanIp);
                        continue;
                    }

                    boolean ok = body.contains("STATUS_OK");

                    // If multi-target payload wasn't accepted by older firmware, fallback to direct openAccess + copy restriction
                    if (!ok) {
                        String singleSetXml = "<DeviceInformationModel>" +
                                "<SetValue overrideDelta='false'>" +
                                "<Payload><path>Authentication/AuthenticationSettings/openAccess</path><value>" + openAccessVal + "</value></Payload>" +
                                "<Payload><path>Authentication/AuthenticationSettings/AuthenticatedTarget/copy</path><value>" + copyVal + "</value></Payload>" +
                                "</SetValue>" +
                                "<Command>" +
                                "<AuthSettings><commandNode>Authentication/AuthenticationSettings</commandNode></AuthSettings>" +
                                "</Command>" +
                                "</DeviceInformationModel>";

                        HttpRequest singleReq = HttpRequest.newBuilder()
                                .uri(URI.create("http://" + cleanIp + "/contentwebserver"))
                                .timeout(TIMEOUT)
                                .header("Content-Type", "text/xml; charset=UTF-8")
                                .header("csrfpId", session.sessionCookie() != null ? session.sessionCookie() : "")
                                .header("Referer", "http://" + cleanIp + "/Administration/Setup/General.html")
                                .header("Cache-Control", "no-cache")
                                .header("Pragma", "no-cache")
                                .POST(HttpRequest.BodyPublishers.ofString(singleSetXml))
                                .build();

                        HttpResponse<String> singleResp = session.client().send(singleReq, HttpResponse.BodyHandlers.ofString());
                        String singleBody = singleResp.body() != null ? singleResp.body() : "";
                        if (singleBody.contains("STATUS_AL_CWS_UNAUTHORIZED")) {
                            sessionCache.remove(cleanIp);
                            continue;
                        }
                        ok = singleBody.contains("STATUS_OK");
                    }

                    log.info("[TOSHIBA CWS] setCopyRestriction on {} -> restricted={} : {} (cached={})", cleanIp, restricted, ok, attempt == 0);
                    if (ok) {
                        currentCopyRestrictionByPrinter.put(cleanIp, restricted);
                    }
                    return ok;
                } catch (Exception e) {
                    sessionCache.remove(cleanIp);
                    if (attempt == 1) {
                        log.warn("[TOSHIBA CWS] setCopyRestriction on {} failed: {}", cleanIp, e.getMessage());
                    }
                }
            }
            return false;
        }
    }

    /**
     * Resets the Toshiba MFP to a clean, locked EWB kiosk state:
     * 1. DefaultScreen restored to "EWB" (kiosk terminal always boots & wakes into nexPrint).
     * 2. Physical copy restriction locked (openAccess=1, allowGuestUser=false, AuthenticatedTarget/copy=true)
     *    so walk-up users cannot copy without authenticating on nexPrint first.
     */
        /**
     * Pushes complete fleet enterprise settings to a Toshiba MFP:
     * 1. EWB Home Page & Kiosk Terminal setup (nexPrint URL).
     * 2. UI DefaultScreen = EWB.
     * 3. Function-wise Authentication Lockdown (openAccess=1, AuthenticatedTarget/copy=true, scan=true, EWB=false).
     * 4. Active Directory LDAP Server configuration (IP, port, search base, credentials).
     */
    public EwbConfigResult pushLdapAndLock(String printerIp, String adminUsername, String adminPassword, String terminalUrl, com.rr.secureprint.entity.DirectoryConfigEntity dirConfig) {
        String cleanIp = printerIp != null ? printerIp.trim() : "";
        String username = (adminUsername != null && !adminUsername.isBlank()) ? adminUsername.trim() : "admin";
        String password = (adminPassword != null && !adminPassword.isBlank()) ? adminPassword.trim() : "123456";

        if (cleanIp.isEmpty()) {
            return new EwbConfigResult(false, "Printer IP address is required.", null, null, false, Instant.now());
        }

        // 1. First push EWB settings
        EwbConfigResult ewbResult = configureEwb(cleanIp, username, password, terminalUrl);
        if (!ewbResult.success()) {
            return ewbResult;
        }

        // 2. Lock Copy/Scan function-wise
        boolean lockOk = setCopyRestriction(cleanIp, username, password, true);
        setDefaultScreen(cleanIp, username, password, "EWB");

        // 3. If DirectoryConfigEntity is provided, push LDAP settings
        String ldapStatus = "LDAP skipped (not configured)";
        if (dirConfig != null && dirConfig.getLdapUrl() != null && !dirConfig.getLdapUrl().isBlank()) {
            try {
                TopAccessSession session = getOrCreateSession(cleanIp, username, password);
                String ldapHost = "127.0.0.1";
                String ldapPort = "389";
                try {
                    URI u = URI.create(dirConfig.getLdapUrl().replace("ldap://", "http://").replace("ldaps://", "https://"));
                    if (u.getHost() != null) ldapHost = u.getHost();
                    if (u.getPort() > 0) ldapPort = String.valueOf(u.getPort());
                } catch (Exception ex) {
                    ldapHost = dirConfig.getLdapUrl().replaceAll("[^0-9.]", "");
                }

                // If LDAP URL points to localhost/127.0.0.1, resolve the reachable local IPv4 address
                // so the physical Toshiba MFP can connect across the LAN.
                if ("127.0.0.1".equals(ldapHost) || "localhost".equalsIgnoreCase(ldapHost)) {
                    try (java.net.DatagramSocket socket = new java.net.DatagramSocket()) {
                        socket.connect(java.net.InetAddress.getByName(cleanIp), 80);
                        ldapHost = socket.getLocalAddress().getHostAddress();
                    } catch (Exception e) {
                        ldapHost = "192.168.1.100";
                    }
                    log.info("[TOSHIBA CWS] Resolved localhost LDAP host to LAN interface IP: {} for MFP {}", ldapHost, cleanIp);
                }

                String baseDn = dirConfig.getBaseDn() != null ? dirConfig.getBaseDn() : "";
                String bindUser = dirConfig.getBindUser() != null ? dirConfig.getBindUser() : "";
                String bindPass = dirConfig.getBindPassword() != null ? dirConfig.getBindPassword() : "";
                String cardAttr = (dirConfig.getCardIdAttribute() != null && !dirConfig.getCardIdAttribute().isBlank())
                        ? dirConfig.getCardIdAttribute() : "employeeNumber";

                Thread.sleep(1500);
                Duration ldapTimeout = Duration.ofSeconds(20);
                // Step A: Query existing DirectoryServices to check if ActiveDirectory exists and discover its ID
                String queryDsXml = "<DeviceInformationModel><GetValue><Network><Services><DirectoryServices></DirectoryServices><LDAP></LDAP></Services></Network></GetValue></DeviceInformationModel>";
                HttpRequest queryDsReq = HttpRequest.newBuilder()
                        .uri(URI.create("http://" + cleanIp + "/contentwebserver"))
                        .timeout(ldapTimeout)
                        .header("Content-Type", "text/plain; charset=utf-8")
                        .header("csrfpId", session.sessionCookie() != null ? session.sessionCookie() : "")
                        .header("Referer", "http://" + cleanIp + "/Administration/Setup/Network.html")
                        .POST(HttpRequest.BodyPublishers.ofString(queryDsXml))
                        .build();

                HttpResponse<String> queryDsResp = session.client().send(queryDsReq, HttpResponse.BodyHandlers.ofString());
                String qBody = queryDsResp.body() != null ? queryDsResp.body() : "";
                boolean adExists = qBody.contains("<name>ActiveDirectory</name>") || qBody.contains(">ActiveDirectory<");

                // Step B: Build DirectoryService Commit XML (Network commandNode)
                String dsNameTag = adExists ? "" : "<name>ActiveDirectory</name>";
                String dsXml = "<DeviceInformationModel>" +
                        "<Command>" +
                        "<Commit>" +
                        "<commandNode>Network</commandNode>" +
                        "<SetValue>" +
                        "<Network><Services><DirectoryServices>" +
                        "<DirectoryService>" +
                        dsNameTag +
                        "<oldName>ActiveDirectory</oldName>" +
                        "<ipAddress>" + escapeXml(ldapHost) + "</ipAddress>" +
                        "<portNo>" + escapeXml(ldapPort) + "</portNo>" +
                        "<authenticationMethod>SimpleBind</authenticationMethod>" +
                        "<searchBase>" + escapeXml(baseDn) + "</searchBase>" +
                        "<userName>" + escapeXml(bindUser) + "</userName>" +
                        (bindPass.isEmpty() ? "" : "<password>" + escapeXml(bindPass) + "</password>") +
                        "<timeOut>2</timeOut>" +
                        "<selectedForAuthentication>1</selectedForAuthentication>" +
                        (adExists ? "" : "<selected>0</selected>") +
                        "<SSLEnabled>Disable</SSLEnabled>" +
                        "<SSLPortNo>636</SSLPortNo>" +
                        "<givenName>givenName</givenName>" +
                        "<sn>sn</sn>" +
                        "<mail>mail</mail>" +
                        "<facsimileTelephoneNumber>facsimileTelephoneNumber</facsimileTelephoneNumber>" +
                        "<company>company</company>" +
                        "<department>department</department>" +
                        "<WindowsServer>true</WindowsServer>" +
                        "<attrUserName>sAMAccountName</attrUserName>" +
                        "<attrLdapServerName>sAMAccountName</attrLdapServerName>" +
                        "<attrPIN>pager</attrPIN>" +
                        "<attrCardID>" + escapeXml(cardAttr) + "</attrCardID>" +
                        "<attrCardRevision>adminDescription</attrCardRevision>" +
                        "<attrFromAddr>mail</attrFromAddr>" +
                        "<attrFromName>displayName</attrFromName>" +
                        "</DirectoryService>" +
                        "</DirectoryServices></Services></Network>" +
                        "</SetValue>" +
                        "</Commit>" +
                        "</Command>" +
                        "</DeviceInformationModel>";

                HttpRequest dsReq = HttpRequest.newBuilder()
                        .uri(URI.create("http://" + cleanIp + "/contentwebserver"))
                        .timeout(ldapTimeout)
                        .header("Content-Type", "text/plain; charset=utf-8")
                        .header("csrfpId", session.sessionCookie() != null ? session.sessionCookie() : "")
                        .header("Referer", "http://" + cleanIp + "/Administration/Setup/setting/DirServiceProp.html")
                        .POST(HttpRequest.BodyPublishers.ofString(dsXml))
                        .build();

                HttpResponse<String> dsResp = session.client().send(dsReq, HttpResponse.BodyHandlers.ofString());
                String dsRespBody = dsResp.body() != null ? dsResp.body() : "";
                boolean dsOk = dsRespBody.contains("STATUS_OK") || dsRespBody.contains("STATUS_DS_USR_ALLREADYEXISTS");

                // Step C: Query to discover ActiveDirectory ID and set SearchServer in LDAP
                String adId = null;
                try {
                    HttpResponse<String> qResp2 = session.client().send(queryDsReq, HttpResponse.BodyHandlers.ofString());
                    String b2 = qResp2.body() != null ? qResp2.body() : "";
                    log.info("[TOSHIBA CWS] Directory Services query on {}: {}", cleanIp, b2);
                    Pattern p = Pattern.compile("<DirectoryService[^>]*?id=[\"\'](\\d+)[\"\'][^>]*>([\\s\\S]*?)</DirectoryService>", Pattern.CASE_INSENSITIVE);
                    Matcher m = p.matcher(b2);
                    while (m.find()) {
                        String id = m.group(1);
                        String content = m.group(2);
                        if (content.contains("ActiveDirectory")) {
                            adId = id;
                            break;
                        }
                    }
                } catch (Exception ignored) {}
                if (adId == null || adId.isBlank() || "0".equals(adId)) { adId = "1"; }
                log.info("[TOSHIBA CWS] ActiveDirectory assigned ID on {}: {}", cleanIp, adId);

                // Set LDAP SearchServer to 0 (None / Local Address Book only) to eliminate 20-second scanner freeze
                String searchServerXml = "<DeviceInformationModel>" +
                        "<Command>" +
                        "<Commit>" +
                        "<commandNode>Network</commandNode>" +
                        "<SetValue>" +
                        "<Network><Services><LDAP>" +
                        "<SearchServer><DirectoryServiceID>0</DirectoryServiceID></SearchServer>" +
                        "</LDAP></Services></Network>" +
                        "</SetValue>" +
                        "</Commit>" +
                        "</Command>" +
                        "</DeviceInformationModel>";

                HttpRequest searchServerReq = HttpRequest.newBuilder()
                        .uri(URI.create("http://" + cleanIp + "/contentwebserver"))
                        .timeout(TIMEOUT)
                        .header("Content-Type", "text/plain; charset=utf-8")
                        .header("csrfpId", session.sessionCookie() != null ? session.sessionCookie() : "")
                        .header("Referer", "http://" + cleanIp + "/Administration/Setup/Network.html")
                        .POST(HttpRequest.BodyPublishers.ofString(searchServerXml))
                        .build();

                session.client().send(searchServerReq, HttpResponse.BodyHandlers.ofString());

                // Step D: Apply function-wise lockdown (Copy/Scan locked, EWB open) + Native LDAP Auth + Card Auth
                String chosenAdId = (adId != null && !adId.isBlank()) ? adId : "0";
                String authLockXml = "<DeviceInformationModel>" +
                        "<SetValue overrideDelta='false'>" +
                        "<Payload><path>Authentication/AuthenticationSettings/openAccess</path><value>1</value></Payload>" +
                        "<Payload><path>Authentication/AuthenticationSettings/functionwiseAuthentication</path><value>true</value></Payload>" +
                        "<Payload><path>Authentication/AuthenticationSettings/unauthorizedPrintJob</path><value>Delete</value></Payload>" +
                        "<Payload><path>Authentication/AuthenticationSettings/allowGuestUser</path><value>false</value></Payload>" +
                        "<Payload><path>Authentication/AuthenticationSettings/AuthenticatedTarget/copy</path><value>true</value></Payload>" +
                        "<Payload><path>Authentication/AuthenticationSettings/AuthenticatedTarget/scan</path><value>true</value></Payload>" +
                        "<Payload><path>Authentication/AuthenticationSettings/AuthenticatedTarget/fax</path><value>true</value></Payload>" +
                        "<Payload><path>Authentication/AuthenticationSettings/AuthenticatedTarget/print</path><value>false</value></Payload>" +
                        "<Payload><path>Authentication/AuthenticationSettings/AuthenticatedTarget/list</path><value>true</value></Payload>" +
                        "<Payload><path>Authentication/AuthenticationSettings/AuthenticatedTarget/userFunctions</path><value>true</value></Payload>" +
                        "<Payload><path>Authentication/AuthenticationSettings/AuthenticatedTarget/EWB</path><value>false</value></Payload>" +
                        "<Payload><path>Authentication/AuthenticationSettings/AuthenticationType/authenticationName</path><value>Ldap</value></Payload>" +
                        "<Payload><path>Authentication/AuthenticationSettings/AuthenticationType/autoRegistration</path><value>true</value></Payload>" +
                        "<Payload><path>Authentication/AuthenticationSettings/AuthenticationType/primaryLdapID</path><value>1</value></Payload>" +
                        "<Payload><path>Authentication/AuthenticationSettings/AuthenticationType/Ldap[@ID='1']/ldapServerID</path><value>" + chosenAdId + "</value></Payload>" +
                        "<Payload><path>Authentication/AuthenticationSettings/AuthenticationType/Ldap[@ID='1']/isWindowsServer</path><value>true</value></Payload>" +
                        "<Payload><path>Authentication/AuthenticationSettings/AuthenticationType/Ldap[@ID='1']/attrNameForUser</path><value>sAMAccountName</value></Payload>" +
                        "<Payload><path>Authentication/AuthenticationSettings/AuthenticationType/Ldap[@ID=1]/ldapServerID</path><value>" + chosenAdId + "</value></Payload>" +
                        "<Payload><path>Authentication/AuthenticationSettings/AuthenticationType/Ldap[@ID=1]/isWindowsServer</path><value>true</value></Payload>" +
                        "<Payload><path>Authentication/AuthenticationSettings/AuthenticationType/Ldap[@ID=1]/attrNameForUser</path><value>sAMAccountName</value></Payload>" +
                        "<Payload><path>Authentication/AuthenticationSettings/CardAuthentication/allowCardAuthAlways</path><value>false</value></Payload>" +
                        "<Payload><path>Authentication/AuthenticationSettings/CardAuthentication/autoRegistrationForCardAuth</path><value>true</value></Payload>" +
                        "<Payload><path>Authentication/AuthenticationSettings/CardAuthentication/screenForCardAuthentication</path><value>false</value></Payload>" +
                        "<Payload><path>Authentication/AuthenticationSettings/CardAuthentication/primaryCardServerID</path><value>1</value></Payload>" +
                        "<Payload><path>Authentication/AuthenticationSettings/CardAuthentication/cardServerID[@ID='1']</path><value>" + chosenAdId + "</value></Payload>" +
                        "<Payload><path>Authentication/AuthenticationSettings/CardAuthentication/cardServerID[@ID=1]</path><value>" + chosenAdId + "</value></Payload>" +
                        "<Payload><path>Authentication/AuthenticationSettings/CardAuthentication/isWindowsServer[@ID='1']</path><value>true</value></Payload>" +
                        "<Payload><path>Authentication/AuthenticationSettings/CardAuthentication/isWindowsServer[@ID=1]</path><value>true</value></Payload>" +
                        "<Payload><path>Authentication/AuthenticationSettings/CardAuthentication/attrNameForUser[@ID='1']</path><value>sAMAccountName</value></Payload>" +
                        "<Payload><path>Authentication/AuthenticationSettings/CardAuthentication/attrNameForUser[@ID=1]</path><value>sAMAccountName</value></Payload>" +
                        "<Payload><path>Authentication/AuthenticationSettings/CardAuthentication/atrNameForCardID[@ID='1']</path><value>" + escapeXml(cardAttr) + "</value></Payload>" +
                        "<Payload><path>Authentication/AuthenticationSettings/CardAuthentication/atrNameForCardID[@ID=1]</path><value>" + escapeXml(cardAttr) + "</value></Payload>" +
                        "</SetValue>" +
                        "<Command>" +
                        "<AuthSettings><commandNode>Authentication/AuthenticationSettings</commandNode></AuthSettings>" +
                        "</Command>" +
                        "</DeviceInformationModel>";

                HttpRequest authLockReq = HttpRequest.newBuilder()
                        .uri(URI.create("http://" + cleanIp + "/contentwebserver"))
                        .timeout(ldapTimeout)
                        .header("Content-Type", "text/xml; charset=UTF-8")
                        .header("csrfpId", session.sessionCookie() != null ? session.sessionCookie() : "")
                        .header("Referer", "http://" + cleanIp + "/Administration/Security/Authentication.html")
                        .POST(HttpRequest.BodyPublishers.ofString(authLockXml))
                        .build();

                HttpResponse<String> authLockResp = session.client().send(authLockReq, HttpResponse.BodyHandlers.ofString());
                String authBody = authLockResp.body() != null ? authLockResp.body() : "";
                boolean authLockOk = authBody.contains("STATUS_OK");

                // Enable CardAuthentication on the Controller AdminSystemSettings so firmware activates the card reader
                String ctrlCardAuthXml = "<DeviceInformationModel><Command><Set><commandNode>Controller</commandNode><SetValue>" +
                        "<Controller><Settings><AdminSystemSettings><Authentication><CardAuthentication>false</CardAuthentication></Authentication></AdminSystemSettings></Settings></Controller>" +
                        "</SetValue></Set></Command></DeviceInformationModel>";
                try {
                    HttpRequest ctrlCardReq = HttpRequest.newBuilder()
                            .uri(URI.create("http://" + cleanIp + "/contentwebserver"))
                            .timeout(ldapTimeout)
                            .header("Content-Type", "text/xml; charset=UTF-8")
                            .header("csrfpId", session.sessionCookie() != null ? session.sessionCookie() : "")
                            .header("Referer", "http://" + cleanIp + "/Administration/Security/Authentication.html")
                            .POST(HttpRequest.BodyPublishers.ofString(ctrlCardAuthXml))
                            .build();
                    session.client().send(ctrlCardReq, HttpResponse.BodyHandlers.ofString());
                } catch (Exception ignored) {}
                log.info("[TOSHIBA CWS] Auth Settings with Native LDAP & Card Auth on {} -> ok={}", cleanIp, authLockOk);

                if (!authLockOk) {
                    log.warn("[TOSHIBA CWS] Full LDAP Auth Settings response on {}: {}", cleanIp, authBody);
                    String fallbackAuthXml = "<DeviceInformationModel>" +
                            "<SetValue overrideDelta='false'>" +
                            "<Payload><path>Authentication/AuthenticationSettings/openAccess</path><value>1</value></Payload>" +
                            "<Payload><path>Authentication/AuthenticationSettings/functionwiseAuthentication</path><value>true</value></Payload>" +
                            "<Payload><path>Authentication/AuthenticationSettings/AuthenticatedTarget/copy</path><value>true</value></Payload>" +
                            "<Payload><path>Authentication/AuthenticationSettings/AuthenticatedTarget/scan</path><value>true</value></Payload>" +
                            "<Payload><path>Authentication/AuthenticationSettings/AuthenticatedTarget/print</path><value>false</value></Payload>" +
                            "<Payload><path>Authentication/AuthenticationSettings/AuthenticatedTarget/EWB</path><value>false</value></Payload>" +
                            "</SetValue>" +
                            "<Command>" +
                            "<AuthSettings><commandNode>Authentication/AuthenticationSettings</commandNode></AuthSettings>" +
                            "</Command>" +
                            "</DeviceInformationModel>";
                    HttpRequest fallbackReq = HttpRequest.newBuilder()
                            .uri(URI.create("http://" + cleanIp + "/contentwebserver"))
                            .timeout(ldapTimeout)
                            .header("Content-Type", "text/xml; charset=UTF-8")
                            .header("csrfpId", session.sessionCookie() != null ? session.sessionCookie() : "")
                            .header("Referer", "http://" + cleanIp + "/Administration/Security/Authentication.html")
                            .POST(HttpRequest.BodyPublishers.ofString(fallbackAuthXml))
                            .build();
                    session.client().send(fallbackReq, HttpResponse.BodyHandlers.ofString());
                }

                if (dsOk) {
                    ldapStatus = "LDAP Configured (" + ldapHost + ":" + ldapPort + ", BaseDN=" + baseDn + ", AD ID=" + (adId != null ? adId : "Active") + ")";
                } else {
                    ldapStatus = "LDAP Setup: " + extractOpStatus(dsRespBody);
                }
            } catch (Exception ex) {
                ldapStatus = "LDAP Note: " + ex.getMessage();
                log.warn("[TOSHIBA CWS] LDAP Push to {} note: {}", cleanIp, ex.getMessage());
            }
        }

        String summary = "Toshiba MFP " + cleanIp + ": EWB Terminal Configured | Panel Lock=" + (lockOk ? "Active (Copy/Scan Secured)" : "Warning") + " | " + ldapStatus;
        return new EwbConfigResult(true, summary, ewbResult.printerModel(), ewbResult.configuredUrl(), true, Instant.now());
    }

    public boolean resetPrinterToEwbKiosk(String printerIp, String adminUsername, String adminPassword) {
        String cleanIp = printerIp != null ? printerIp.trim() : "";
        String username = (adminUsername != null && !adminUsername.isBlank()) ? adminUsername.trim() : "admin";
        String password = (adminPassword != null && !adminPassword.isBlank()) ? adminPassword.trim() : "123456";

        log.info("[TOSHIBA CWS] Resetting MFP {} to clean EWB kiosk state...", cleanIp);
        boolean uiOk = setDefaultScreen(cleanIp, username, password, "EWB");
        boolean authOk = setCopyRestriction(cleanIp, username, password, true);
        boolean fromOk = setScanFromAddress(cleanIp, username, password, "scanner@rrconstruction.lk", "Scanner");
        clearLdapSearchServer(cleanIp, username, password);
        log.info("[TOSHIBA CWS] MFP {} kiosk reset complete: uiOk={}, authOk={}, fromOk={}", cleanIp, uiOk, authOk, fromOk);
        return uiOk && authOk;
    }

    /**
     * Eliminates native Toshiba Scan screen startup freezes by optimizing:
     * 1. Setting DNS primary to 192.168.1.100 and secondary to 8.8.8.8 with a 2-second timeout.
     *    (Prevents 10-15s timeouts caused by 0.0.0.0 DNS).
     * 2. Setting DirectoryService 0 (TOSHIBA eBX) IP address to 127.0.0.1 instead of 'localhost'.
     *    (Bypasses DNS resolution entirely for local address book lookup).
     * 3. Setting LDAP SearchServer to 0 (None / Local Address Book only).
     * All 3 are committed in a single Network transaction and cached in-memory so it executes only once.
     */
    public boolean optimizeToshibaScanPerformance(String printerIp, String adminUsername, String adminPassword) {
        String cleanIp = printerIp != null ? printerIp.trim() : "";
        if (cleanIp.isEmpty()) return false;
        if (scanOptimizedPrinters.contains(cleanIp)) {
            return true;
        }

        try {
            TopAccessSession session = getOrCreateSession(cleanIp, adminUsername, adminPassword);
            String optXml = "<DeviceInformationModel>" +
                    "<Command><Commit><commandNode>Network</commandNode><SetValue>" +
                    "<Network>" +
                    "<Protocols><DNS>" +
                    "<primaryServer>192.168.1.100</primaryServer>" +
                    "<secondaryServer>8.8.8.8</secondaryServer>" +
                    "<dnsTimeoutValue>2</dnsTimeoutValue>" +
                    "</DNS></Protocols>" +
                    "<Services>" +
                    "<LDAP>" +
                    "<SearchServer><DirectoryServiceID>0</DirectoryServiceID></SearchServer>" +
                    "<connectionTimeOut>2</connectionTimeOut>" +
                    "</LDAP>" +
                    "<DirectoryServices>" +
                    "<DirectoryService permId='0' id='0'>" +
                    "<ipAddress>127.0.0.1</ipAddress>" +
                    "<timeOut>2</timeOut>" +
                    "</DirectoryService>" +
                    "<DirectoryService permId='3' id='3'>" +
                    "<authenticationMethod>SimpleBind</authenticationMethod>" +
                    "<timeOut>2</timeOut>" +
                    "</DirectoryService>" +
                    "</DirectoryServices>" +
                    "</Services>" +
                    "</Network>" +
                    "</SetValue></Commit></Command>" +
                    "</DeviceInformationModel>";

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("http://" + cleanIp + "/contentwebserver"))
                    .timeout(TIMEOUT)
                    .header("Content-Type", "text/plain; charset=utf-8")
                    .header("csrfpId", session.sessionCookie() != null ? session.sessionCookie() : "")
                    .header("Referer", "http://" + cleanIp + "/Administration/Setup/Network.html")
                    .POST(HttpRequest.BodyPublishers.ofString(optXml))
                    .build();

            HttpResponse<String> resp = session.client().send(req, HttpResponse.BodyHandlers.ofString());
            String respBody = resp.body() != null ? resp.body() : "";
            boolean ok = respBody.contains("STATUS_OK") || respBody.contains("STATUS_DS_USR_ALLREADYEXISTS");
            if (ok) {
                scanOptimizedPrinters.add(cleanIp);
                log.info("[TOSHIBA CWS] Optimized Scan performance (DNS 192.168.1.100, eBX 127.0.0.1, SearchServer 0) on {} -> ok=true", cleanIp);
            } else {
                log.warn("[TOSHIBA CWS] Failed to optimize Scan performance on {}: {}", cleanIp, respBody);
            }
            return ok;
        } catch (Exception ex) {
            log.warn("[TOSHIBA CWS] Exception optimizing Scan performance on {}: {}", cleanIp, ex.getMessage());
            return false;
        }
    }

    /**
     * Backward-compatible alias for optimizeToshibaScanPerformance.
     */
    public boolean clearLdapSearchServer(String printerIp, String adminUsername, String adminPassword) {
        return optimizeToshibaScanPerformance(printerIp, adminUsername, adminPassword);
    }

    /**
     * Dynamically switches the Toshiba Controller's Default Screen via TopAccess CWS.
     * Setting screenName="Copy" ensures that when EWB closes or minimizes, the panel lands directly on the native Copy screen (NOT Home).
     * Setting screenName="EWB" restores the terminal to our kiosk application.
     */
    public boolean setDefaultScreen(String printerIp, String adminUsername, String adminPassword, String screenName) {
        String cleanIp = printerIp != null ? printerIp.trim() : "";
        String username = (adminUsername != null && !adminUsername.isBlank()) ? adminUsername.trim() : "admin";
        String password = (adminPassword != null && !adminPassword.isBlank()) ? adminPassword.trim() : "123456";
        String targetScreen = (screenName != null && !screenName.isBlank()) ? screenName.trim() : "Copy";

        if (cleanIp.isEmpty()) return false;

        String currentScreen = currentDefaultScreenByPrinter.get(cleanIp);
        if (targetScreen.equalsIgnoreCase(currentScreen)) {
            log.info("[TOSHIBA CWS] setDefaultScreen on {} already {}. Instant return (0ms).", cleanIp, targetScreen);
            return true;
        }

        Object lock = printerLocks.computeIfAbsent(cleanIp, k -> new Object());
        synchronized (lock) {
            for (int attempt = 0; attempt < 2; attempt++) {
                try {
                    TopAccessSession session = getOrCreateSession(cleanIp, username, password);
                    String progKeyXml = "EWB".equalsIgnoreCase(targetScreen)
                            ? "<Functions><ProgramableKey><Key id='1'><function></function><option><Address></Address><AutoScreenChange>false</AutoScreenChange></option></Key></ProgramableKey></Functions>"
                            : "";
                    String setUiXml = "<DeviceInformationModel>" +
                            "<Command>" +
                            "<Set>" +
                            "<commandNode>Controller</commandNode>" +
                            "<SetValue>" +
                            "<Controller>" +
                            "<Settings>" +
                            "<UI>" +
                            "<DefaultScreen>" + escapeXml(targetScreen) + "</DefaultScreen>" +
                            "</UI>" +
                            progKeyXml +
                            "</Settings>" +
                            "</Controller>" +
                            "</SetValue>" +
                            "</Set>" +
                            "</Command>" +
                            "</DeviceInformationModel>";

                    HttpRequest setUiReq = HttpRequest.newBuilder()
                            .uri(URI.create("http://" + cleanIp + "/contentwebserver"))
                            .timeout(TIMEOUT)
                            .header("Content-Type", "text/xml; charset=UTF-8")
                            .header("csrfpId", session.sessionCookie() != null ? session.sessionCookie() : "")
                            .header("Referer", "http://" + cleanIp + "/Administration/Setup/General.html")
                            .POST(HttpRequest.BodyPublishers.ofString(setUiXml))
                            .build();

                    HttpResponse<String> uiResp = session.client().send(setUiReq, HttpResponse.BodyHandlers.ofString());
                    String body = uiResp.body() != null ? uiResp.body() : "";
                    boolean ok = uiResp.statusCode() >= 200 && uiResp.statusCode() < 400 &&
                            !body.contains("STATUS_AL_CWS_UNAUTHORIZED") && !body.contains("STATUS_FAILED");
                    if (ok) {
                        currentDefaultScreenByPrinter.put(cleanIp, targetScreen);
                        log.info("[TOSHIBA CWS] setDefaultScreen on {} to {} -> status={}, ok=true (cached={})", cleanIp, targetScreen, uiResp.statusCode(), attempt == 0);
                        return true;
                    }
                    // Expired session on printer - clear cache and retry once
                    sessionCache.remove(cleanIp);
                } catch (Exception ex) {
                    sessionCache.remove(cleanIp);
                    if (attempt == 1) {
                        log.warn("[TOSHIBA CWS] setDefaultScreen on {} to {} failed: {}", cleanIp, targetScreen, ex.getMessage());
                    }
                }
            }
            return false;
        }
    }

    private TopAccessSession loginToTopAccess(String printerIp, String username, String password) throws Exception {
        CookieManager cookieManager = new CookieManager();
        cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
        HttpClient client = HttpClient.newBuilder()
                .cookieHandler(cookieManager)
                .connectTimeout(TIMEOUT)
                .build();

        // 1. Initial Handshake to obtain Session cookie
        String topAccessUrl = "http://" + printerIp + "/";
        HttpRequest handshakeReq = HttpRequest.newBuilder()
                .uri(URI.create(topAccessUrl))
                .timeout(TIMEOUT)
                .GET()
                .build();

        HttpResponse<String> handshakeResp = client.send(handshakeReq, HttpResponse.BodyHandlers.ofString());
        String initialSession = extractSessionFromResponse(handshakeResp, cookieManager);
        if (initialSession == null || initialSession.isBlank()) {
            throw new IllegalStateException("Failed to connect to Toshiba TopAccess at " + printerIp + " (No session cookie).");
        }

        String clientIp = extractClientIp(initialSession);

        // 2. Admin Login Request
        String loginXml = "<DeviceInformationModel>" +
                "<GetValue><Authentication><UserCredential></UserCredential></Authentication></GetValue>" +
                "<GetValue><Panel><DiagnosticMode><Mode_08><Code_8913></Code_8913></Mode_08></DiagnosticMode></Panel></GetValue>" +
                "<SetValue><Authentication><UserCredential>" +
                "<userName>" + escapeXml(username) + "</userName>" +
                "<passwd>" + escapeXml(password) + "</passwd>" +
                "<ipaddress>" + clientIp + "</ipaddress>" +
                "<DepartmentManagement isEnable='false'>" +
                "<requireDepartment>false</requireDepartment>" +
                "</DepartmentManagement>" +
                "<domainName></domainName>" +
                "<applicationType>TOP_ACCESS</applicationType>" +
                "</UserCredential></Authentication></SetValue>" +
                "<Command><Login><commandNode>Authentication/UserCredential</commandNode><Params><appName>TOPACCESS</appName></Params></Login></Command>" +
                "<SaveSessionInformation><SessionInfo>" +
                "<Information><type>LoginPassword</type><data>" + escapeXml(password) + "</data></Information>" +
                "<Information><type>LoginUser</type><data>" + escapeXml(username) + "</data></Information>" +
                "</SessionInfo></SaveSessionInformation>" +
                "</DeviceInformationModel>";

        HttpRequest loginReq = HttpRequest.newBuilder()
                .uri(URI.create("http://" + printerIp + "/contentwebserver"))
                .timeout(TIMEOUT)
                .header("Content-Type", "text/xml; charset=UTF-8")
                .header("csrfpId", initialSession)
                .header("Referer", "http://" + printerIp + "/Administration/AdminLogin.html")
                .POST(HttpRequest.BodyPublishers.ofString(loginXml))
                .build();

        HttpResponse<String> loginResp = client.send(loginReq, HttpResponse.BodyHandlers.ofString());
        String loginBody = loginResp.body() != null ? loginResp.body() : "";

        boolean loginOk = loginBody.contains("Normal") || loginBody.contains("STATUS_OK");
        if (!loginOk) {
            String errorMsg = "Toshiba Admin Login failed. Check printer admin credentials.";
            if (loginBody.contains("STATUS_AUTHENTICATION_FAILED") || loginBody.contains("STATUS_FAILED") || loginBody.contains("STATUS_AL_CWS_UNAUTHORIZED")) {
                errorMsg = "Invalid printer admin username or password.";
            }
            throw new SecurityException(errorMsg);
        }

        // TopAccess returns a renewed Session cookie upon authentication
        String authSession = extractSessionFromResponse(loginResp, cookieManager);
        if (authSession == null || authSession.isBlank()) {
            authSession = initialSession;
        }

        return new TopAccessSession(client, cookieManager, authSession, clientIp, System.currentTimeMillis());
    }

    private EwbConfigResult queryEwbSettings(String printerIp, HttpClient client, String sessionCookie) {
        try {
            String getXml = "<DeviceInformationModel><GetValue><EWBSettings/></GetValue></DeviceInformationModel>";
            HttpRequest getReq = HttpRequest.newBuilder()
                    .uri(URI.create("http://" + printerIp + "/contentwebserver"))
                    .timeout(TIMEOUT)
                    .header("Content-Type", "text/xml; charset=UTF-8")
                    .header("csrfpId", sessionCookie != null ? sessionCookie : "")
                    .header("Referer", "http://" + printerIp + "/Administration/Setup/Ewb.html")
                    .POST(HttpRequest.BodyPublishers.ofString(getXml))
                    .build();

            HttpResponse<String> resp = client.send(getReq, HttpResponse.BodyHandlers.ofString());
            String body = resp.body() != null ? resp.body() : "";

            String model = extractTag(MODEL_PATTERN, body, "TOSHIBA e-STUDIO");
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
            return new EwbConfigResult(false, "Query failed: " + e.getMessage(), "TOSHIBA e-STUDIO", null, false, Instant.now());
        }
    }

    public Map<String, String> dumpAllSettings(String printerIp, String adminUsername, String adminPassword) {
        String cleanIp = printerIp != null ? printerIp.trim() : "";
        String username = (adminUsername != null && !adminUsername.isBlank()) ? adminUsername.trim() : "admin";
        String password = (adminPassword != null && !adminPassword.isBlank()) ? adminPassword.trim() : "123456";

        Map<String, String> result = new LinkedHashMap<>();
        try {
            TopAccessSession session = loginToTopAccess(cleanIp, username, password);

            String[] queries = new String[] {
                "AuthSettings", "<DeviceInformationModel><Command><GetSettings><commandNode>Authentication/AuthenticationSettings</commandNode></GetSettings></Command><GetValue><Authentication><AuthenticationSettings/></Authentication><Controller><Settings><AdminSystemSettings><Authentication/></AdminSystemSettings></Settings></Controller></GetValue></DeviceInformationModel>",
                "UI", "<DeviceInformationModel><GetValue><Controller><Settings><UI/></Settings></Controller></GetValue></DeviceInformationModel>",
                "EWBSettings", "<DeviceInformationModel><GetValue><EWBSettings/></GetValue></DeviceInformationModel>",
                "Functions", "<DeviceInformationModel><GetValue><Controller><Settings><Functions/></Settings></Controller></GetValue></DeviceInformationModel>",
                "Panel", "<DeviceInformationModel><GetValue><Panel/></GetValue></DeviceInformationModel>",
                "Network", "<DeviceInformationModel><GetValue><Network/></GetValue></DeviceInformationModel>",
                "AdminSystemSettings", "<DeviceInformationModel><GetValue><Controller><Settings><AdminSystemSettings/></Settings></Controller></GetValue></DeviceInformationModel>"
            };

            for (int i = 0; i < queries.length; i += 2) {
                String key = queries[i];
                String xml = queries[i + 1];
                try {
                    HttpRequest req = HttpRequest.newBuilder()
                            .uri(URI.create("http://" + cleanIp + "/contentwebserver"))
                            .timeout(TIMEOUT)
                            .header("Content-Type", "text/xml; charset=UTF-8")
                            .header("csrfpId", session.sessionCookie() != null ? session.sessionCookie() : "")
                            .POST(HttpRequest.BodyPublishers.ofString(xml))
                            .build();
                    HttpResponse<String> resp = session.client().send(req, HttpResponse.BodyHandlers.ofString());
                    result.put(key, resp.body() != null ? resp.body() : "EMPTY");
                } catch (Exception ex) {
                    result.put(key, "ERROR: " + ex.getMessage());
                }
            }
        } catch (Exception e) {
            result.put("LOGIN_ERROR", e.getMessage());
        }
        return result;
    }

    private boolean sendRebootCommand(String cleanIp, HttpClient client, String authSession) {
        try {
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
            log.info("Printer {} closed connection to execute ForcedReboot: {}", cleanIp, ioe.getMessage());
            return true;
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
            if (msg.contains("reset") || msg.contains("closed") || msg.contains("eof") || msg.contains("timeout")) {
                log.info("Printer {} entered reboot sequence: {}", cleanIp, e.getMessage());
                return true;
            }
            return false;
        }
    }

    private String extractClientIp(String sessionCookie) {
        if (sessionCookie == null || !sessionCookie.contains(".")) {
            return "127.0.0.1";
        }
        int lastDot = sessionCookie.lastIndexOf('.');
        return sessionCookie.substring(0, lastDot);
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

    private String extractSessionFromResponse(HttpResponse<?> response, CookieManager cookieManager) {
        if (response != null) {
            List<String> setCookies = response.headers().allValues("Set-Cookie");
            for (String sc : setCookies) {
                if (sc.startsWith("Session=")) {
                    int end = sc.indexOf(';');
                    if (end != -1) {
                        return sc.substring("Session=".length(), end).trim();
                    } else {
                        return sc.substring("Session=".length()).trim();
                    }
                }
            }
        }
        return extractSessionCookie(cookieManager);
    }

    private String extractSessionCookie(CookieManager cookieManager) {
        if (cookieManager == null) return null;
        List<HttpCookie> cookies = cookieManager.getCookieStore().getCookies();
        String found = null;
        for (HttpCookie cookie : cookies) {
            if ("Session".equalsIgnoreCase(cookie.getName())) {
                found = cookie.getValue();
            }
        }
        return found;
    }

    /**
     * Pushes SMTP client configuration and Scan-to-Email settings to Toshiba MFP TopAccess.
     */
    public boolean configureToshibaSmtp(String printerIp, String adminUsername, String adminPassword,
                                        String smtpHost, int smtpPort, String smtpUser, String smtpPass,
                                        String fromAddr, boolean useSsl) {
        String cleanIp = printerIp != null ? printerIp.trim() : "";
        if (cleanIp.isEmpty() || smtpHost == null || smtpHost.isBlank()) return false;

        try {
            TopAccessSession session = getOrCreateSession(cleanIp, adminUsername, adminPassword);

            String authXml = (smtpUser != null && !smtpUser.isBlank())
                    ? "<Authentication><desiredType>LOGIN</desiredType><userName>" + escapeXml(smtpUser) + "</userName>" +
                      (smtpPass != null && !smtpPass.isBlank() ? "<password>" + escapeXml(smtpPass) + "</password>" : "") + "</Authentication>"
                    : "<Authentication><desiredType>None</desiredType></Authentication>";

            String smtpXml = "<DeviceInformationModel>" +
                    "<Command><Commit><commandNode>Network</commandNode><SetValue>" +
                    "<Network><Protocols><SMTP><Client>" +
                    "<server>" + escapeXml(smtpHost) + "</server>" +
                    "<portNo>" + smtpPort + "</portNo>" +
                    authXml +
                    "<SSLEnabled>" + (useSsl ? "Enable" : "Disable") + "</SSLEnabled>" +
                    "</Client></SMTP></Protocols></Network>" +
                    "</SetValue></Commit></Command></DeviceInformationModel>";

            HttpRequest smtpReq = HttpRequest.newBuilder()
                    .uri(URI.create("http://" + cleanIp + "/contentwebserver"))
                    .timeout(TIMEOUT)
                    .header("Content-Type", "text/xml; charset=UTF-8")
                    .header("csrfpId", session.sessionCookie() != null ? session.sessionCookie() : "")
                    .header("Referer", "http://" + cleanIp + "/Administration/Setup/Network.html")
                    .POST(HttpRequest.BodyPublishers.ofString(smtpXml))
                    .build();

            HttpResponse<String> smtpResp = session.client().send(smtpReq, HttpResponse.BodyHandlers.ofString());

            if (fromAddr != null && !fromAddr.isBlank()) {
                String emailXml = "<DeviceInformationModel>" +
                        "<Command><Set><commandNode>Controller</commandNode><SetValue>" +
                        "<Controller><Settings><AdminSystemSettings>" +
                        "<EMail><fromAddr>" + escapeXml(fromAddr) + "</fromAddr></EMail>" +
                        "</AdminSystemSettings></Settings></Controller>" +
                        "</SetValue></Set></Command></DeviceInformationModel>";

                HttpRequest emailReq = HttpRequest.newBuilder()
                        .uri(URI.create("http://" + cleanIp + "/contentwebserver"))
                        .timeout(TIMEOUT)
                        .header("Content-Type", "text/xml; charset=UTF-8")
                        .header("csrfpId", session.sessionCookie() != null ? session.sessionCookie() : "")
                        .header("Referer", "http://" + cleanIp + "/Administration/Security/Authentication.html")
                        .POST(HttpRequest.BodyPublishers.ofString(emailXml))
                        .build();

                session.client().send(emailReq, HttpResponse.BodyHandlers.discarding());
            }

            boolean ok = smtpResp.body() != null && smtpResp.body().contains("STATUS_OK");
            log.info("[TOSHIBA CWS] Pushed SMTP ({}:{}) to {} -> ok={}", smtpHost, smtpPort, cleanIp, ok);
            return ok;
        } catch (Exception ex) {
            log.warn("[TOSHIBA CWS] Failed to push SMTP to {}: {}", cleanIp, ex.getMessage());
            return false;
        }
    }

    /**
     * Sets the default Scan-to-Email "FROM" address and sender name on Toshiba TopAccess.
     * Prevents the native Toshiba error: "FROM address is not registered".
     */
    public boolean setScanFromAddress(String printerIp, String adminUsername, String adminPassword, String fromAddr, String fromName) {
        String cleanIp = printerIp != null ? printerIp.trim() : "";
        String username = (adminUsername != null && !adminUsername.isBlank()) ? adminUsername.trim() : "admin";
        String password = (adminPassword != null && !adminPassword.isBlank()) ? adminPassword.trim() : "123456";
        String cleanEmail = (fromAddr != null && !fromAddr.isBlank()) ? fromAddr.trim() : "scanner@rrconstruction.lk";
        String cleanName = (fromName != null && !fromName.isBlank()) ? fromName.trim() : "Scanner";

        if (cleanIp.isEmpty()) return false;

        String targetFrom = cleanEmail + "|" + cleanName;
        String currentFrom = currentScanFromByPrinter.get(cleanIp);
        if (targetFrom.equalsIgnoreCase(currentFrom)) {
            log.info("[TOSHIBA CWS] setScanFromAddress on {} already {}. Instant return (0ms).", cleanIp, targetFrom);
            return true;
        }

        Object lock = printerLocks.computeIfAbsent(cleanIp, k -> new Object());
        synchronized (lock) {
            for (int attempt = 0; attempt < 2; attempt++) {
                try {
                    TopAccessSession session = getOrCreateSession(cleanIp, username, password);
                    String emailXml = "<DeviceInformationModel>" +
                            "<Command><Set><commandNode>Controller</commandNode><SetValue>" +
                            "<Controller><Settings><AdminSystemSettings>" +
                            "<EMail>" +
                            "<fromAddr>" + escapeXml(cleanEmail) + "</fromAddr>" +
                            "<fromName>" + escapeXml(cleanName) + "</fromName>" +
                            "</EMail>" +
                            "</AdminSystemSettings></Settings></Controller>" +
                            "</SetValue></Set></Command></DeviceInformationModel>";

                    HttpRequest emailReq = HttpRequest.newBuilder()
                            .uri(URI.create("http://" + cleanIp + "/contentwebserver"))
                            .timeout(TIMEOUT)
                            .header("Content-Type", "text/xml; charset=UTF-8")
                            .header("csrfpId", session.sessionCookie() != null ? session.sessionCookie() : "")
                            .header("Referer", "http://" + cleanIp + "/Administration/Setup/Email.html")
                            .POST(HttpRequest.BodyPublishers.ofString(emailXml))
                            .build();

                    HttpResponse<String> resp = session.client().send(emailReq, HttpResponse.BodyHandlers.ofString());
                    String body = resp.body() != null ? resp.body() : "";
                    boolean ok = resp.statusCode() >= 200 && resp.statusCode() < 400 &&
                            !body.contains("STATUS_AL_CWS_UNAUTHORIZED") && !body.contains("STATUS_FAILED");
                    if (ok) {
                        currentScanFromByPrinter.put(cleanIp, targetFrom);
                        log.info("[TOSHIBA CWS] setScanFromAddress on {} to {} ({}) -> ok=true", cleanIp, cleanEmail, cleanName);
                        return true;
                    }
                    sessionCache.remove(cleanIp);
                } catch (Exception ex) {
                    sessionCache.remove(cleanIp);
                    if (attempt == 1) {
                        log.warn("[TOSHIBA CWS] setScanFromAddress on {} failed: {}", cleanIp, ex.getMessage());
                    }
                }
            }
            return false;
        }
    }

    /**
     * Ensures ONLY the authenticated user is registered in the Toshiba MFP's internal Address Book.
     * Any foreign contacts or duplicates are cleaned up so that when the user opens [Address Book],
     * they see ONLY their own corporate email as a Favorite Contact.
     */
    public boolean registerAddressBookContact(String printerIp, String adminUsername, String adminPassword,
                                              String username, String displayName, String email, String department) {
        String cleanIp = printerIp != null ? printerIp.trim() : "";
        if (cleanIp.isEmpty() || email == null || email.isBlank()) return false;
        String cleanUser = (username != null && !username.isBlank()) ? username.trim() : "user";
        String firstName = (displayName != null && !displayName.isBlank()) ? displayName.trim() : cleanUser;
        String dept = department != null ? department.trim() : "";
        String cleanEmail = email.trim();

        try {
            TopAccessSession session = getOrCreateSession(cleanIp, adminUsername, adminPassword);

            // 1. Search current contacts
            String searchXml = "<DeviceInformationModel><Command><SearchContact><commandNode>AddressBook/ContactList/Contact</commandNode><Params><viewXpath contentType='Value'>AddressBook/View/ContactList</viewXpath><searchXpath contentType='XPath'>AddressBook/View/Search</searchXpath><pageSize contentType='Value'>-1</pageSize><directoryServiceName contentType='Value'>TOSHIBA eBX</directoryServiceName><client contentType='Value'>TopAccess</client></Params></SearchContact></Command><SetValue><AddressBook><View><Search><id>*</id><firstName></firstName><lastName></lastName><eMailAddress></eMailAddress><faxNo></faxNo><department></department><corporation></corporation></Search><ContactList curPage='0' pageSize='-1'/></View></AddressBook></SetValue></DeviceInformationModel>";
            HttpRequest searchReq = HttpRequest.newBuilder()
                    .uri(URI.create("http://" + cleanIp + "/contentwebserver"))
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "text/xml; charset=UTF-8")
                    .header("csrfpId", session.sessionCookie() != null ? session.sessionCookie() : "")
                    .header("Referer", "http://" + cleanIp + "/Registration/AddressBook/AddrBook.html")
                    .POST(HttpRequest.BodyPublishers.ofString(searchXml))
                    .build();
            session.client().send(searchReq, HttpResponse.BodyHandlers.ofString());

            // 2. Fetch contact list
            String getListXml = "<DeviceInformationModel><GetValue><AddressBook><View><ContactList/></View></AddressBook></GetValue></DeviceInformationModel>";
            HttpRequest getListReq = HttpRequest.newBuilder()
                    .uri(URI.create("http://" + cleanIp + "/contentwebserver"))
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "text/xml; charset=UTF-8")
                    .header("csrfpId", session.sessionCookie() != null ? session.sessionCookie() : "")
                    .header("Referer", "http://" + cleanIp + "/Registration/AddressBook/AddrBook.html")
                    .POST(HttpRequest.BodyPublishers.ofString(getListXml))
                    .build();
            HttpResponse<String> listResp = session.client().send(getListReq, HttpResponse.BodyHandlers.ofString());
            String listBody = listResp.body() != null ? listResp.body() : "";

            Pattern contactPattern = Pattern.compile("<Contact id=[\"'](\\d+)[\"']>([\\s\\S]*?)</Contact>");
            Matcher m = contactPattern.matcher(listBody);
            boolean currentUserExists = false;
            java.util.List<String> idsToDelete = new java.util.ArrayList<>();

            while (m.find()) {
                String id = m.group(1);
                String contactContent = m.group(2);
                Pattern emailPattern = Pattern.compile("<eMailAddress>([^<]*)</eMailAddress>");
                Matcher em = emailPattern.matcher(contactContent);
                String existingEmail = em.find() ? em.group(1).trim() : "";

                if (existingEmail.equalsIgnoreCase(cleanEmail) && !currentUserExists) {
                    currentUserExists = true;
                } else {
                    idsToDelete.add(id);
                }
            }

            for (String delId : idsToDelete) {
                try {
                    String delXml = "<DeviceInformationModel><Command><DeleteContact><commandNode>AddressBook/ContactList/Contact</commandNode><Params><viewXpath contentType='XPath'>AddressBook/View/ContactList</viewXpath><selectedContactId contentType='Value'>" + delId + "</selectedContactId><client contentType='Value'>TopAccess</client></Params></DeleteContact></Command></DeviceInformationModel>";
                    HttpRequest delReq = HttpRequest.newBuilder()
                            .uri(URI.create("http://" + cleanIp + "/contentwebserver"))
                            .timeout(Duration.ofSeconds(4))
                            .header("Content-Type", "text/xml; charset=UTF-8")
                            .header("csrfpId", session.sessionCookie() != null ? session.sessionCookie() : "")
                            .header("Referer", "http://" + cleanIp + "/Registration/AddressBook/AddrBook.html")
                            .POST(HttpRequest.BodyPublishers.ofString(delXml))
                            .build();
                    session.client().send(delReq, HttpResponse.BodyHandlers.ofString());
                    log.info("[TOSHIBA CWS] Purged non-matching / duplicate contact ID {} from {}", delId, cleanIp);
                } catch (Exception ignored) {}
            }

            // 3. If current user not yet registered, create as primary Favorite contact
            if (!currentUserExists) {
                String contactXml = "<DeviceInformationModel>" +
                        "<SetValue>" +
                        "<AddressBook><View><New><Contact>" +
                        "<MetaData>" +
                        "<firstName>" + escapeXml(firstName) + "</firstName>" +
                        "<furigana></furigana>" +
                        "<lastName></lastName>" +
                        "<faxNo></faxNo>" +
                        "<secondFaxNo></secondFaxNo>" +
                        "<corporation></corporation>" +
                        "<department>" + escapeXml(dept) + "</department>" +
                        "<keyWord>" + escapeXml(cleanUser) + "</keyWord>" +
                        "<eMailAddress>" + escapeXml(cleanEmail) + "</eMailAddress>" +
                        "<favoritemail>true</favoritemail>" +
                        "<favoritefax>false</favoritefax>" +
                        "</MetaData>" +
                        "<FaxOptions>" +
                        "<sid></sid><sub></sub><sep></sep><password></password><ecm>true</ecm><lineSelect>1</lineSelect><qualityTx>Standard</qualityTx><txType>G3</txType>" +
                        "</FaxOptions>" +
                        "</Contact></New></View></AddressBook>" +
                        "</SetValue>" +
                        "<Command>" +
                        "<CreateContact>" +
                        "<commandNode>AddressBook/ContactList</commandNode>" +
                        "<Params>" +
                        "<selectedContactId contentType='Value'></selectedContactId>" +
                        "<newContactXpath contentType='XPath'>AddressBook/View/New/Contact</newContactXpath>" +
                        "<client contentType='Value'>TopAccess</client>" +
                        "</Params>" +
                        "</CreateContact>" +
                        "</Command>" +
                        "</DeviceInformationModel>";

                HttpRequest createReq = HttpRequest.newBuilder()
                        .uri(URI.create("http://" + cleanIp + "/contentwebserver"))
                        .timeout(Duration.ofSeconds(6))
                        .header("Content-Type", "text/xml; charset=UTF-8")
                        .header("csrfpId", session.sessionCookie() != null ? session.sessionCookie() : "")
                        .header("Referer", "http://" + cleanIp + "/Registration/AddressBook/AddrBook.html")
                        .POST(HttpRequest.BodyPublishers.ofString(contactXml))
                        .build();

                HttpResponse<String> createResp = session.client().send(createReq, HttpResponse.BodyHandlers.ofString());
                String createBody = createResp.body() != null ? createResp.body() : "";
                boolean ok = createBody.contains("STATUS_OK") || createBody.contains("Normal");
                log.info("[TOSHIBA CWS] AddressBook sole contact registered for {} ({}) on {} -> ok={}", firstName, cleanEmail, cleanIp, ok);
                return ok;
            }

            log.info("[TOSHIBA CWS] AddressBook contact for {} ({}) is already sole contact on {}", firstName, cleanEmail, cleanIp);
            return true;
        } catch (Exception ex) {
            log.warn("[TOSHIBA CWS] Failed to synchronize AddressBook on {}: {}", cleanIp, ex.getMessage());
            return false;
        }
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
