package com.rr.secureprint.service;

import com.rr.secureprint.dto.CopyJobDto;
import com.rr.secureprint.entity.CopyJobEntity;
import com.rr.secureprint.entity.PrinterEntity;
import com.rr.secureprint.entity.UserEntity;
import com.rr.secureprint.repository.CopyJobRepository;
import com.rr.secureprint.repository.PrinterRepository;
import com.rr.secureprint.repository.UserRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class CopyTrackingService {

    private static final Logger LOGGER = Logger.getLogger(CopyTrackingService.class.getName());
    private static final Duration TIMEOUT = Duration.ofSeconds(6);

    private final CopyJobRepository copyJobRepository;
    private final UserRepository userRepository;
    private final PrinterRepository printerRepository;
    private final AuditService auditService;
    private final ToshibaEwbService toshibaEwbService;
    private final HttpClient httpClient;

    /**
     * Represents a terminal session on a specific printer.
     */
    public record TerminalSession(String username, Instant loginTime, Instant logoutTime, Instant lastHeartbeatTime) {
        public TerminalSession(String username, Instant loginTime, Instant logoutTime) {
            this(username, loginTime, logoutTime, loginTime != null ? loginTime : Instant.now());
        }

        public boolean isActive() {
            return logoutTime == null;
        }

        public boolean matchesJob(Instant jobCompletedAt) {
            if (jobCompletedAt == null) return false;
            // 90s before login to account for slight clock skew between MFP & Server
            Instant start = loginTime.minusSeconds(90);
            // 180s after logout (or now + 180s if still active) to account for job completion logging
            Instant end = (logoutTime != null ? logoutTime : Instant.now()).plusSeconds(180);
            return !jobCompletedAt.isBefore(start) && !jobCompletedAt.isAfter(end);
        }
    }

    // Active terminal sessions mapped by client or printer IP
    private final Map<String, TerminalSession> activeTerminalUsers = new ConcurrentHashMap<>();

    // Session history per printer/client IP (last 50 sessions retained for reconciliation)
    private final Map<String, ConcurrentLinkedDeque<TerminalSession>> sessionHistory = new ConcurrentHashMap<>();

    public CopyTrackingService(CopyJobRepository copyJobRepository,
                               UserRepository userRepository,
                               PrinterRepository printerRepository,
                               AuditService auditService,
                               ToshibaEwbService toshibaEwbService) {
        this.copyJobRepository = copyJobRepository;
        this.userRepository = userRepository;
        this.printerRepository = printerRepository;
        this.auditService = auditService;
        this.toshibaEwbService = toshibaEwbService;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .build();
    }

    /**
     * Returns a set of all printer/client IPs currently holding an active terminal session.
     */
    public Set<String> getActiveTerminalIps() {
        return new HashSet<>(activeTerminalUsers.keySet());
    }

    /**
     * Updates the last heartbeat timestamp for an active terminal session.
     */
    public void updateSessionHeartbeat(String clientOrPrinterIp) {
        if (clientOrPrinterIp == null) return;
        String cleanIp = clientOrPrinterIp.trim();
        TerminalSession current = activeTerminalUsers.get(cleanIp);
        if (current != null && current.isActive()) {
            activeTerminalUsers.put(cleanIp, new TerminalSession(current.username(), current.loginTime(), null, Instant.now()));
        }
    }

    /**
     * Registers a user who has authenticated at the touchscreen terminal on a specific printer or client IP.
     */
    public void registerActiveTerminalUser(String clientOrPrinterIp, String username) {
        if (clientOrPrinterIp == null || username == null || username.isBlank()) return;
        String cleanIp = clientOrPrinterIp.trim();
        String cleanUser = normalizeUsername(username);

        Instant now = Instant.now();
        TerminalSession session = new TerminalSession(cleanUser, now, null, now);
        activeTerminalUsers.put(cleanIp, session);
        addToHistory(cleanIp, session);

        LOGGER.info("[TERMINAL SESSION] Registered active user '" + cleanUser + "' on IP " + cleanIp);
    }

    /**
     * Closes the active terminal session upon logout and triggers an immediate fast poll on the printer.
     */
    public void recordTerminalLogout(String clientOrPrinterIp) {
        if (clientOrPrinterIp == null) return;
        String cleanIp = clientOrPrinterIp.trim();

        TerminalSession active = activeTerminalUsers.remove(cleanIp);
        if (active != null) {
            TerminalSession closed = new TerminalSession(active.username(), active.loginTime(), Instant.now());
            addToHistory(cleanIp, closed);
            LOGGER.info("[TERMINAL SESSION] User '" + active.username() + "' logged out from IP " + cleanIp + " (Session recorded for job reconciliation)");
        }

        // Re-lock copy restriction on the Toshiba MFP to prevent unauthorized walk-up copies (e-BRIDGE Next)
        printerRepository.findByIpAddress(cleanIp).ifPresent(p -> {
            if (!"TOSHIBA_OLD".equalsIgnoreCase(p.getDeviceGeneration())) {
                CompletableFuture.runAsync(() -> {
                    try {
                        toshibaEwbService.setCopyRestriction(p.getIpAddress(), p.getAdminUsername(), p.getAdminPassword(), true);
                    } catch (Exception ex) {
                        LOGGER.fine("Could not re-lock copy restriction on " + cleanIp + ": " + ex.getMessage());
                    }
                });
            }
        });

        // Immediately trigger async poll for this printer to capture any copies completed just before logout
        triggerAsyncPrinterPoll(cleanIp);
    }

    /**
     * Backward-compatible logout helper.
     */
    public void clearActiveTerminalUser(String clientIp) {
        recordTerminalLogout(clientIp);
    }

    /**
     * Returns the user currently active on the specified printer IP.
     */
    public String getActiveTerminalUser(String printerIp) {
        return resolveTerminalUserForJob(printerIp, Instant.now());
    }

    /**
     * Resolves the effective username for a photocopy job on a printer based on session history and timestamps.
     */
    public String resolveTerminalUserForJob(String printerIp, Instant completedAt) {
        if (printerIp == null) return null;
        String cleanIp = printerIp.trim();

        // 1. Check printer-specific session history for matching time-window
        ConcurrentLinkedDeque<TerminalSession> history = sessionHistory.get(cleanIp);
        if (history != null && completedAt != null) {
            for (TerminalSession s : history) {
                if (s.matchesJob(completedAt)) {
                    LOGGER.info("[COPY ATTRIBUTION] Matched job at " + completedAt + " on " + cleanIp + " to session user '" + s.username() + "'");
                    return s.username();
                }
            }
        }

        // 2. Check currently active session on this printer
        TerminalSession active = activeTerminalUsers.get(cleanIp);
        if (active != null) {
            LOGGER.info("[COPY ATTRIBUTION] Attributing job on " + cleanIp + " to currently active user '" + active.username() + "'");
            return active.username();
        }

        // 3. Fallback: Check most recent session on this printer within 15 minutes
        if (history != null && !history.isEmpty()) {
            TerminalSession recent = history.peekFirst();
            if (recent != null) {
                Instant refTime = recent.logoutTime() != null ? recent.logoutTime() : recent.loginTime();
                if (Duration.between(refTime, Instant.now()).toMinutes() < 15) {
                    LOGGER.info("[COPY ATTRIBUTION] Attributing job on " + cleanIp + " to recent session user '" + recent.username() + "'");
                    return recent.username();
                }
            }
        }

        // 4. Fallback: If only 1 terminal session active system-wide (single-station demo)
        if (activeTerminalUsers.size() == 1) {
            TerminalSession singleActive = activeTerminalUsers.values().iterator().next();
            LOGGER.info("[COPY ATTRIBUTION] Attributing job on " + cleanIp + " to single active user '" + singleActive.username() + "'");
            return singleActive.username();
        }

        return null;
    }

    private void addToHistory(String ip, TerminalSession session) {
        sessionHistory.compute(ip, (k, deque) -> {
            if (deque == null) {
                deque = new ConcurrentLinkedDeque<>();
            }
            deque.addFirst(session);
            while (deque.size() > 50) {
                deque.pollLast();
            }
            return deque;
        });
    }

    /**
     * Asynchronously triggers a single-printer photocopy poll.
     */
    public void triggerAsyncPrinterPoll(String ipOrCode) {
        CompletableFuture.runAsync(() -> {
            try {
                Optional<PrinterEntity> printerOpt = printerRepository.findByIpAddress(ipOrCode);
                if (printerOpt.isEmpty()) {
                    printerOpt = printerRepository.findByPrinterCode(ipOrCode.toUpperCase());
                }
                printerOpt.ifPresent(this::pollPrinterJobLogs);
            } catch (Exception ex) {
                LOGGER.fine("[COPY POLL] Async poll trigger error: " + ex.getMessage());
            }
        });
    }

    /**
     * Record a walk-up photocopy job against a specific user account.
     * Idempotent: duplicate printer job IDs are automatically ignored.
     */
    @Transactional
    public CopyJobEntity recordCopyJob(String rawUsername, String printerIp, String printerName,
                                       int pages, int colorPages, int monoPages,
                                       String printerJobId, Instant completedAt) {
        if (printerJobId != null && !printerJobId.isBlank()) {
            if (copyJobRepository.existsByPrinterIpAndPrinterJobId(printerIp, printerJobId)) {
                LOGGER.fine("[COPY TRACK] Job " + printerJobId + " on " + printerIp + " already recorded. Skipping.");
                return null;
            }
        }

        String cleanUsername = normalizeUsername(rawUsername);
        Optional<UserEntity> userOpt = userRepository.findByUsernameIgnoreCase(cleanUsername);

        UserEntity user = userOpt.orElse(null);
        int finalPages = Math.max(pages, 1);

        if (user != null) {
            user.setCopyCount(user.getCopyCount() + finalPages);
            userRepository.save(user);
            LOGGER.info("[COPY TRACK] User '" + cleanUsername + "' updated with +" + finalPages + " photocopy pages (Total copies: " + user.getCopyCount() + ")");
        } else {
            LOGGER.info("[COPY TRACK] Walk-up user '" + rawUsername + "' recorded on printer " + printerIp + " (" + finalPages + " pages).");
        }

        Optional<PrinterEntity> printerOpt = (printerIp != null && !printerIp.isBlank())
                ? printerRepository.findByIpAddress(printerIp)
                : Optional.empty();

        CopyJobEntity copyJob = new CopyJobEntity(
                user,
                printerOpt.orElse(null),
                cleanUsername,
                printerName != null ? printerName : (printerOpt.map(PrinterEntity::getName).orElse("Toshiba MFP")),
                printerIp,
                finalPages,
                colorPages,
                monoPages,
                printerJobId,
                completedAt != null ? completedAt : Instant.now()
        );

        CopyJobEntity saved = copyJobRepository.save(copyJob);

        auditService.logEvent("COPY_JOB_COMPLETED",
                user != null ? user.getUserSid() : "SYSTEM",
                cleanUsername,
                null,
                printerOpt.map(PrinterEntity::getPrinterCode).orElse("MFP"),
                printerIp,
                "Photocopied " + finalPages + " pages (Mono: " + monoPages + ", Color: " + colorPages + ") on " +
                        (printerName != null ? printerName : "Toshiba MFP") + " [" + printerIp + "]",
                "SUCCESS");

        return saved;
    }

    /**
     * Automatic Sleep & Inactivity Watchdog: Runs every 5 seconds.
     * Detects if an MFP has gone to sleep or been abandoned (no heartbeat for > 20s,
     * or maximum walk-up copy session exceeded 90s).
     * Immediately re-locks the physical Toshiba MFP via CWS so nobody can make unauthorized copies.
     */
    @Scheduled(fixedDelay = 5000, initialDelay = 5000)
    public void autoLockInactiveOrSleepingTerminals() {
        if (activeTerminalUsers.isEmpty()) return;

        Instant now = Instant.now();
        List<String> ipsToLock = new ArrayList<>();

        for (Map.Entry<String, TerminalSession> entry : activeTerminalUsers.entrySet()) {
            String ip = entry.getKey();
            TerminalSession session = entry.getValue();
            if (session.isActive()) {
                Instant lastHb = session.lastHeartbeatTime() != null ? session.lastHeartbeatTime() : session.loginTime();
                long secondsSinceHeartbeat = Duration.between(lastHb, now).getSeconds();
                long totalSessionSeconds = Duration.between(session.loginTime(), now).getSeconds();

                // If no heartbeat for > 20 seconds (e.g. physical Sleep button pressed) OR total session > 90 seconds
                if (secondsSinceHeartbeat > 20 || totalSessionSeconds > 90) {
                    LOGGER.info("[AUTO-LOCK WATCHDOG] Terminal on IP " + ip + " entered Sleep or Inactivity (" +
                            secondsSinceHeartbeat + "s without heartbeat, user: '" + session.username() + "'). Auto-locking physical device now.");
                    ipsToLock.add(ip);
                }
            }
        }

        for (String ip : ipsToLock) {
            recordTerminalLogout(ip);
            auditService.logEvent("DEVICE_AUTO_LOCKED", "SYSTEM", "watchdog", null,
                    "MFP", ip,
                    "Physical device automatically locked: Sleep mode or inactivity detected", "SUCCESS");
        }
    }

    /**
     * Scheduled background job: Polls all active physical Toshiba MFPs every 60 seconds
     * for recently completed photocopy jobs.
     */
    @Scheduled(fixedDelay = 60000, initialDelay = 15000)
    public void pollFleetPhotocopyJobs() {
        List<PrinterEntity> physicalPrinters = printerRepository.findAll().stream()
                .filter(PrinterEntity::isActive)
                .filter(p -> !"VIRTUAL-QUEUE".equalsIgnoreCase(p.getPrinterCode()))
                .filter(p -> p.getIpAddress() != null && !p.getIpAddress().isBlank())
                .toList();

        for (PrinterEntity printer : physicalPrinters) {
            try {
                pollPrinterJobLogs(printer);
            } catch (Exception e) {
                LOGGER.fine("[COPY POLL] Error polling " + printer.getName() + " (" + printer.getIpAddress() + "): " + e.getMessage());
            }
        }
    }

    /**
     * Poll a specific Toshiba printer for recent completed COPY jobs.
     */
    public void pollPrinterJobLogs(PrinterEntity printer) {
        if ("TOSHIBA_OLD".equalsIgnoreCase(printer.getDeviceGeneration())) {
            // Older Toshiba models (e-STUDIO 306SE, 256, 356, 456) do not expose the e-BRIDGE Next CWS XML RetrieveLog endpoint
            return;
        }
        String ip = printer.getIpAddress().trim();
        String adminUser = (printer.getAdminUsername() != null && !printer.getAdminUsername().isBlank())
                ? printer.getAdminUsername().trim() : "admin";
        String adminPass = (printer.getAdminPassword() != null && !printer.getAdminPassword().isBlank())
                ? printer.getAdminPassword().trim() : "123456";

        try {
            // 1. Initial Handshake to obtain Session cookie
            HttpRequest hsReq = HttpRequest.newBuilder()
                    .uri(URI.create("http://" + ip + "/"))
                    .timeout(TIMEOUT)
                    .GET()
                    .build();
            HttpResponse<String> hsResp = httpClient.send(hsReq, HttpResponse.BodyHandlers.ofString());
            String cookie = hsResp.headers().firstValue("set-cookie").orElse("");
            String session = "";
            Matcher m = Pattern.compile("Session=([^;]+)").matcher(cookie);
            if (m.find()) {
                session = m.group(1);
            }

            if (session.isBlank()) {
                LOGGER.fine("[COPY POLL] No session cookie from " + ip);
                return;
            }

            // Extract client IP prefix from session cookie (e.g. 192.168.1.100.xxxx)
            String clientIp = "192.168.1.100";
            Matcher ipM = Pattern.compile("^(\\d+\\.\\d+\\.\\d+\\.\\d+)").matcher(session);
            if (ipM.find()) {
                clientIp = ipM.group(1);
            }

            // 2. Admin Login to TopAccess
            String loginXml = "<DeviceInformationModel>" +
                    "<GetValue><Authentication><UserCredential></UserCredential></Authentication></GetValue>" +
                    "<GetValue><Panel><DiagnosticMode><Mode_08><Code_8913></Code_8913></Mode_08></DiagnosticMode></Panel></GetValue>" +
                    "<SetValue><Authentication><UserCredential>" +
                    "<userName>" + adminUser + "</userName>" +
                    "<passwd>" + adminPass + "</passwd>" +
                    "<ipaddress>" + clientIp + "</ipaddress>" +
                    "<DepartmentManagement isEnable='false'><requireDepartment>false</requireDepartment></DepartmentManagement>" +
                    "<domainName></domainName><applicationType>TOP_ACCESS</applicationType>" +
                    "</UserCredential></Authentication></SetValue>" +
                    "<Command><Login><commandNode>Authentication/UserCredential</commandNode><Params><appName>TOPACCESS</appName></Params></Login></Command>" +
                    "<SaveSessionInformation><SessionInfo>" +
                    "<Information><type>LoginPassword</type><data>" + adminPass + "</data></Information>" +
                    "<Information><type>LoginUser</type><data>" + adminUser + "</data></Information>" +
                    "</SessionInfo></SaveSessionInformation>" +
                    "</DeviceInformationModel>";

            HttpRequest loginReq = HttpRequest.newBuilder()
                    .uri(URI.create("http://" + ip + "/contentwebserver"))
                    .timeout(TIMEOUT)
                    .header("Content-Type", "text/xml; charset=UTF-8")
                    .header("Cookie", "Session=" + session)
                    .header("csrfpId", session)
                    .header("Referer", "http://" + ip + "/admin/login.htm")
                    .POST(HttpRequest.BodyPublishers.ofString(loginXml))
                    .build();

            HttpResponse<String> loginResp = httpClient.send(loginReq, HttpResponse.BodyHandlers.ofString());
            String loginBody = loginResp.body() != null ? loginResp.body() : "";
            if (!loginBody.contains("Normal") && !loginBody.contains("STATUS_OK")) {
                LOGGER.fine("[COPY POLL] Login failed on " + ip + " status=" + loginResp.statusCode());
                return;
            }

            // TopAccess may rotate or re-issue the Session cookie upon successful authentication
            for (String setCookieHeader : loginResp.headers().allValues("set-cookie")) {
                Matcher scM = Pattern.compile("Session=([^;]+)").matcher(setCookieHeader);
                if (scM.find()) {
                    session = scM.group(1);
                    break;
                }
            }

            // 3. Query PrintLogs using authentic TopAccess RetrieveLog XML with GetValue
            String logQueryXml = "<DeviceInformationModel>" +
                    "<SetValue>" +
                    "<Log><JobLog pageSize='20' pageNo='1' totalRecords='' totalPages='' snapshot='false'>" +
                    "<PrintLogs><LogRecord>" +
                    "<Common>" +
                    "<RecordNumber/><UserName/><CompletedTime/><ProcessErrorCode/>" +
                    "</Common>" +
                    "<Print>" +
                    "<PrintJobType/><PaperSize/><InputValidPages/><PrintedSets/><DocumentName/>" +
                    "</Print>" +
                    "</LogRecord></PrintLogs></JobLog></Log>" +
                    "</SetValue>" +
                    "<Command>" +
                    "<RetrieveLog>" +
                    "<commandNode>Log</commandNode>" +
                    "<Params>" +
                    "<Order>DESC</Order>" +
                    "<SortCategory>DateTime</SortCategory>" +
                    "<Locale>en-US</Locale>" +
                    "<LogKind>PrintLog</LogKind>" +
                    "</Params>" +
                    "</RetrieveLog>" +
                    "</Command>" +
                    "<GetValue>" +
                    "<Log></Log>" +
                    "</GetValue>" +
                    "</DeviceInformationModel>";

            HttpRequest logReq = HttpRequest.newBuilder()
                    .uri(URI.create("http://" + ip + "/contentwebserver"))
                    .timeout(TIMEOUT)
                    .header("Content-Type", "text/xml; charset=UTF-8")
                    .header("Cookie", "Session=" + session)
                    .header("csrfpId", session)
                    .header("Referer", "http://" + ip + "/admin/login.htm")
                    .POST(HttpRequest.BodyPublishers.ofString(logQueryXml))
                    .build();

            HttpResponse<String> logResp = httpClient.send(logReq, HttpResponse.BodyHandlers.ofString());
            if (logResp.statusCode() == 200 && logResp.body() != null) {
                parseAndRecordToshibaLogRecords(logResp.body(), printer);
            }

            // 4. Clean Admin Logoff
            String logoffXml = "<DeviceInformationModel><Command><Logoff><commandNode>Authentication/UserCredential</commandNode><Params><appName>TOPACCESS</appName></Params></Logoff></Command></DeviceInformationModel>";
            HttpRequest logoffReq = HttpRequest.newBuilder()
                    .uri(URI.create("http://" + ip + "/contentwebserver"))
                    .timeout(Duration.ofSeconds(2))
                    .header("Content-Type", "text/xml; charset=UTF-8")
                    .header("Cookie", "Session=" + session)
                    .header("csrfpId", session)
                    .POST(HttpRequest.BodyPublishers.ofString(logoffXml))
                    .build();
            httpClient.send(logoffReq, HttpResponse.BodyHandlers.discarding());

        } catch (Exception e) {
            LOGGER.fine("[COPY POLL] Exception polling " + ip + ": " + e.getMessage());
        }
    }

    /**
     * Parses TopAccess XML response and persists any newly discovered COPY jobs.
     */
    private void parseAndRecordToshibaLogRecords(String xml, PrinterEntity printer) {
        Pattern recordPattern = Pattern.compile("<LogRecord>(.*?)</LogRecord>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
        Matcher matcher = recordPattern.matcher(xml);

        int count = 0;
        while (matcher.find()) {
            String recordXml = matcher.group(1);

            String jobType = extractTag(recordXml, "PrintJobType");
            if (jobType != null && "Copy".equalsIgnoreCase(jobType.trim())) {
                String recordNumber = extractTag(recordXml, "RecordNumber");
                String rawUser = extractTag(recordXml, "UserName");
                String completedTimeStr = extractTag(recordXml, "CompletedTime");
                String pagesStr = extractTag(recordXml, "InputValidPages");
                String setsStr = extractTag(recordXml, "PrintedSets");

                int validPages = 1;
                try {
                    if (pagesStr != null && !pagesStr.isBlank()) {
                        validPages = Integer.parseInt(pagesStr.trim());
                    }
                } catch (NumberFormatException ignored) {}

                int sets = 1;
                try {
                    if (setsStr != null && !setsStr.isBlank()) {
                        sets = Integer.parseInt(setsStr.trim());
                    }
                } catch (NumberFormatException ignored) {}

                int totalPages = validPages * Math.max(sets, 1);

                Instant completedAt = Instant.now();
                if (completedTimeStr != null && !completedTimeStr.isBlank()) {
                    try {
                        completedAt = LocalDateTime.parse(completedTimeStr.trim()).atZone(ZoneId.systemDefault()).toInstant();
                    } catch (Exception ignored) {}
                }

                // If Toshiba UserName is blank, resolve effective user via session attribution window
                String effectiveUser = rawUser;
                if (effectiveUser == null || effectiveUser.isBlank()) {
                    effectiveUser = resolveTerminalUserForJob(printer.getIpAddress(), completedAt);
                }
                if (effectiveUser == null || effectiveUser.isBlank()) {
                    effectiveUser = "walkup";
                }

                recordCopyJob(effectiveUser, printer.getIpAddress(), printer.getName(), totalPages, 0, totalPages, recordNumber, completedAt);
                count++;
            }
        }
        if (count > 0) {
            LOGGER.info("[COPY POLL] Successfully parsed and recorded " + count + " copy records from " + printer.getName() + " (" + printer.getIpAddress() + ")");
        }
    }

    private String extractTag(String xml, String... tagNames) {
        for (String tag : tagNames) {
            Pattern p = Pattern.compile("<" + tag + "\\b[^>]*>([^<]+)</" + tag + ">", Pattern.CASE_INSENSITIVE);
            Matcher m = p.matcher(xml);
            if (m.find()) {
                return m.group(1).trim();
            }
        }
        return null;
    }

    private String normalizeUsername(String raw) {
        if (raw == null || raw.isBlank()) return "unknown";
        String u = raw.trim();
        // Remove domain prefix e.g. DOMAIN\kamal -> kamal
        if (u.contains("\\")) {
            u = u.substring(u.lastIndexOf('\\') + 1);
        }
        // Remove UPN suffix e.g. kamal@qoa.local -> kamal
        if (u.contains("@")) {
            u = u.substring(0, u.indexOf('@'));
        }
        return u.toLowerCase();
    }

    @Transactional(readOnly = true)
    public List<CopyJobDto> getRecentCopyJobs() {
        return copyJobRepository.findTop50ByOrderByCompletedAtDesc().stream()
                .map(CopyJobDto::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CopyJobDto> getUserCopyJobs(String username) {
        return copyJobRepository.findByUsernameOrderByCompletedAtDesc(normalizeUsername(username)).stream()
                .map(CopyJobDto::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public long getTotalFleetPhotocopies() {
        return copyJobRepository.sumTotalPages();
    }
}
