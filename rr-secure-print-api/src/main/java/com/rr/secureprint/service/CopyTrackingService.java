package com.rr.secureprint.service;

import com.rr.secureprint.dto.CopyJobDto;
import com.rr.secureprint.entity.CopyJobEntity;
import com.rr.secureprint.entity.PrinterEntity;
import com.rr.secureprint.entity.ScanJobEntity;
import com.rr.secureprint.entity.UserEntity;
import com.rr.secureprint.repository.CopyJobRepository;
import com.rr.secureprint.repository.PrinterRepository;
import com.rr.secureprint.repository.ScanJobRepository;
import com.rr.secureprint.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
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
    private final ScanJobRepository scanJobRepository;
    private final HttpClient httpClient;
    private final java.util.Map<String, Long> postJobAutoLockDeadlineByIp = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Represents a terminal session on a specific printer.
     */
    public record TerminalSession(String username, Instant loginTime, Instant logoutTime, Instant lastHeartbeatTime, boolean isWalkUp) {
        public TerminalSession(String username, Instant loginTime, Instant logoutTime) {
            this(username, loginTime, logoutTime, loginTime != null ? loginTime : Instant.now(), false);
        }
        public TerminalSession(String username, Instant loginTime, Instant logoutTime, Instant lastHeartbeatTime) {
            this(username, loginTime, logoutTime, lastHeartbeatTime, false);
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

    @Autowired
    public CopyTrackingService(CopyJobRepository copyJobRepository,
                               UserRepository userRepository,
                               PrinterRepository printerRepository,
                               AuditService auditService,
                               ToshibaEwbService toshibaEwbService,
                               @Autowired(required = false) ScanJobRepository scanJobRepository) {
        this.copyJobRepository = copyJobRepository;
        this.userRepository = userRepository;
        this.printerRepository = printerRepository;
        this.auditService = auditService;
        this.toshibaEwbService = toshibaEwbService;
        this.scanJobRepository = scanJobRepository;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .build();
    }

    public CopyTrackingService(CopyJobRepository copyJobRepository,
                               UserRepository userRepository,
                               PrinterRepository printerRepository,
                               AuditService auditService,
                               ToshibaEwbService toshibaEwbService) {
        this(copyJobRepository, userRepository, printerRepository, auditService, toshibaEwbService, null);
    }

    /**
     * Startup Security Sweep:
     * Guarantees that upon server boot or restart, all active Toshiba physical MFPs
     * are locked down with padlocks and set to display the secure EWB login screen.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void secureAllPrintersOnStartup() {
        LOGGER.info("[STARTUP SWEEP] Running startup security sweep on all Toshiba MFPs...");
        CompletableFuture.runAsync(() -> {
            try {
                // Short 2s delay to ensure Spring context and repositories are completely warm
                Thread.sleep(2000);
                List<PrinterEntity> physicalPrinters = printerRepository.findAll().stream()
                        .filter(PrinterEntity::isActive)
                        .filter(p -> !"VIRTUAL-QUEUE".equalsIgnoreCase(p.getPrinterCode()))
                        .filter(p -> p.getIpAddress() != null && !p.getIpAddress().isBlank())
                        .filter(p -> !"TOSHIBA_OLD".equalsIgnoreCase(p.getDeviceGeneration()))
                        .toList();

                for (PrinterEntity p : physicalPrinters) {
                    try {
                        toshibaEwbService.cacheDefaultScreen(p.getIpAddress(), "EWB");
                        String adminUser = (p.getAdminUsername() != null && !p.getAdminUsername().isBlank()) ? p.getAdminUsername() : "admin";
                        String adminPass = (p.getAdminPassword() != null && !p.getAdminPassword().isBlank()) ? p.getAdminPassword() : "123456";
                        boolean locked = toshibaEwbService.setCopyRestriction(p.getIpAddress(), adminUser, adminPass, true);
                        LOGGER.info("[STARTUP SWEEP] Secured printer " + p.getPrinterCode() + " (" + p.getIpAddress() + "): lock=" + locked);
                    } catch (Exception ex) {
                        LOGGER.warning("[STARTUP SWEEP] Error securing " + p.getPrinterCode() + " (" + p.getIpAddress() + "): " + ex.getMessage());
                    }
                }
            } catch (Exception ex) {
                LOGGER.warning("[STARTUP SWEEP] Startup sweep encountered error: " + ex.getMessage());
            }
        });
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
            activeTerminalUsers.put(cleanIp, new TerminalSession(current.username(), current.loginTime(), null, Instant.now(), current.isWalkUp()));
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
        TerminalSession session = new TerminalSession(cleanUser, now, null, now, false);
        activeTerminalUsers.put(cleanIp, session);
        addToHistory(cleanIp, session);

        // Pre-warm TopAccess session on login so subsequent CWS requests have an active session
        // (Icons remain strictly locked until user explicitly requests Copy or Scan)
        printerRepository.findByIpAddress(cleanIp).ifPresent(p -> {
            if (!"TOSHIBA_OLD".equalsIgnoreCase(p.getDeviceGeneration())) {
                CompletableFuture.runAsync(() -> {
                    try {
                        toshibaEwbService.warmUpSession(p.getIpAddress(), p.getAdminUsername(), p.getAdminPassword());
                    } catch (Exception ignored) {}
                });
            }
        });

        LOGGER.info("[TERMINAL SESSION] Registered active user '" + cleanUser + "' on IP " + cleanIp);
    }

    /**
     * Activates a walk-up copy / scan session where the user interacts directly with the physical MFP glass/panel.
     * Grants extended watchdog tolerances (up to 120s) because background WebKit throttles heartbeat intervals.
     */
    public void registerWalkUpSession(String clientOrPrinterIp, String username) {
        if (clientOrPrinterIp == null || username == null || username.isBlank()) return;
        String cleanIp = clientOrPrinterIp.trim();
        String cleanUser = normalizeUsername(username);

        Instant now = Instant.now();
        TerminalSession session = new TerminalSession(cleanUser, now, null, now, true);
        activeTerminalUsers.put(cleanIp, session);
        addToHistory(cleanIp, session);

        LOGGER.info("[TERMINAL SESSION] Walk-up Copy/Scan mode activated for user '" + cleanUser + "' on IP " + cleanIp);
    }

    /**
     * Closes the active terminal session upon logout and triggers an immediate fast poll on the printer.
     */
    public void recordTerminalLogout(String clientOrPrinterIp) {
        if (clientOrPrinterIp == null) return;
        String cleanIp = clientOrPrinterIp.trim();

        TerminalSession active = activeTerminalUsers.remove(cleanIp);
        postJobAutoLockDeadlineByIp.remove(cleanIp);
        if (active != null) {
            TerminalSession closed = new TerminalSession(active.username(), active.loginTime(), Instant.now());
            addToHistory(cleanIp, closed);
            LOGGER.info("[TERMINAL SESSION] User '" + active.username() + "' logged out from IP " + cleanIp + " (Session recorded for job reconciliation)");
        }

        printerRepository.findByIpAddress(cleanIp).ifPresent(p -> {
            if (!"TOSHIBA_OLD".equalsIgnoreCase(p.getDeviceGeneration())) {
                CompletableFuture.runAsync(() -> {
                    try {
                        toshibaEwbService.setCopyRestriction(p.getIpAddress(), p.getAdminUsername(), p.getAdminPassword(), true);
                        LOGGER.info("[TERMINAL LOGOUT] Copier physical copy restriction locked on " + cleanIp);
                        toshibaEwbService.setDefaultScreen(p.getIpAddress(), p.getAdminUsername(), p.getAdminPassword(), "EWB");
                        if (active != null) {
                            // Poll sequentially AFTER lock is secured so TopAccess never collides
                            pollPrinterJobLogs(p);
                        }
                    } catch (Exception ex) {
                        LOGGER.warning("Could not lock copy on " + cleanIp + ": " + ex.getMessage());
                    }
                });
            } else if (active != null) {
                triggerAsyncPrinterPoll(cleanIp);
            }
        });
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
     * Record a walk-up document scan job against a specific user account.
     * Idempotent: duplicate scan jobs are automatically ignored.
     */
    @Transactional
    public ScanJobEntity recordNativeScanJob(String rawUsername, String printerIp, String printerName,
                                             int pages, String colorMode, String documentName,
                                             String destination, String recordNumber, Instant completedAt) {
        String jobFileName = "TOSHIBA-SCAN-" + (recordNumber != null && !recordNumber.isBlank() ? recordNumber : System.currentTimeMillis()) + ".pdf";
        if (recordNumber != null && !recordNumber.isBlank() && scanJobRepository != null) {
            if (scanJobRepository.existsByPrinterIpAndFileName(printerIp, jobFileName)) {
                LOGGER.fine("[SCAN TRACK] Scan job " + recordNumber + " on " + printerIp + " already recorded. Skipping.");
                return null;
            }
        }

        String cleanUsername = normalizeUsername(rawUsername);
        Optional<UserEntity> userOpt = userRepository.findByUsernameIgnoreCase(cleanUsername);
        UserEntity user = userOpt.orElse(null);
        int finalPages = Math.max(pages, 1);

        if (user != null) {
            user.setScanCount(user.getScanCount() + finalPages);
            userRepository.save(user);
            LOGGER.info("[SCAN TRACK] User '" + cleanUsername + "' updated with +" + finalPages + " scanned pages (Total scans: " + user.getScanCount() + ")");
        } else {
            LOGGER.info("[SCAN TRACK] Walk-up scan user '" + rawUsername + "' recorded on printer " + printerIp + " (" + finalPages + " pages).");
        }

        Optional<PrinterEntity> printerOpt = (printerIp != null && !printerIp.isBlank())
                ? printerRepository.findByIpAddress(printerIp)
                : Optional.empty();

        String recipient = (destination != null && !destination.isBlank()) ? destination
                : (user != null && user.getEmail() != null ? user.getEmail() : "Native MFP Scanner");
        String subject = (documentName != null && !documentName.isBlank()) ? documentName
                : "Document Scanned on " + (printerName != null ? printerName : "Toshiba MFP");

        ScanJobEntity scanJob = new ScanJobEntity(
                user,
                printerOpt.orElse(null),
                cleanUsername,
                printerName != null ? printerName : (printerOpt.map(PrinterEntity::getName).orElse("Toshiba MFP")),
                printerIp,
                recipient,
                subject,
                jobFileName,
                finalPages,
                colorMode != null ? colorMode : "COLOR",
                "300DPI",
                "PDF",
                "SIMPLEX",
                "COMPLETED",
                "Native Toshiba MFP physical scanner job completed"
        );
        if (completedAt != null) {
            scanJob.setCompletedAt(completedAt);
        }

        ScanJobEntity saved = scanJobRepository != null ? scanJobRepository.save(scanJob) : scanJob;

        auditService.logEvent("NATIVE_SCAN_COMPLETED",
                user != null ? user.getUserSid() : "SYSTEM",
                cleanUsername,
                null,
                printerOpt.map(PrinterEntity::getPrinterCode).orElse("MFP"),
                printerIp,
                "Native Document Scan completed: " + finalPages + " page(s) on " +
                        (printerName != null ? printerName : "Toshiba MFP") + " [" + printerIp + "]",
                "SUCCESS");

        return saved;
    }

    /**
     * Fast Poll for Active Walk-Up Printers: disabled to ensure 100% CWS bandwidth
     * is preserved for user panel interactions without any polling lock contention.
     * Reconciliation is performed reliably upon session logout and fleet poll.
     */
    // @Scheduled(fixedDelay = 15000, initialDelay = 10000)
    public void fastPollActiveWalkUpPrinters() {
        if (activeTerminalUsers.isEmpty()) return;
        for (Map.Entry<String, TerminalSession> entry : activeTerminalUsers.entrySet()) {
            if (entry.getValue().isWalkUp()) {
                if (Duration.between(entry.getValue().loginTime(), Instant.now()).getSeconds() < 12) {
                    continue; // Skip immediately after login so unlock has 100% CWS bandwidth
                }
                printerRepository.findByIpAddress(entry.getKey()).ifPresent(p -> {
                    try {
                        pollPrinterJobLogs(p);
                    } catch (Exception ex) {
                        LOGGER.fine("[FAST POLL] Error polling " + p.getIpAddress() + ": " + ex.getMessage());
                    }
                });
            }
        }
    }

    /**
     * Automatic Sleep & Inactivity Watchdog: Runs every 2 seconds.
     * Detects if an MFP has gone to sleep or been abandoned (no heartbeat for > 10s,
     * or maximum walk-up copy session exceeded 180s, or post-job completion timeout).
     * Immediately re-locks the physical Toshiba MFP via CWS so nobody can make unauthorized copies.
     */
    @Scheduled(fixedDelay = 2000, initialDelay = 3000)
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

                Long postJobDeadline = postJobAutoLockDeadlineByIp.get(ip);
                boolean postJobTimeout = postJobDeadline != null && System.currentTimeMillis() >= postJobDeadline;

                // Sleep / Inactivity detection calibrated per Toshiba Series Architecture:
                // Normal heartbeat from active EWB browser is every 2.5s.
                // - Walk-up copy mode: User is operating the physical ADF/glass panel.
                //   On 18 & 28-Series (Chromium), background JS timers are throttled/paused by QtWebEngine.
                //   Therefore, do NOT kill walk-up sessions on missing heartbeats!
                //   Expire walk-up sessions ONLY if:
                //     a) A completed copy job triggered the 15s post-job safety deadline, OR
                //     b) Total walk-up session time exceeded 120s (user walked away without finishing).
                // - Normal EWB mode (PIN screen / Menu / Print Queue):
                //   Browser is running in the foreground. If heartbeats halt for > 20s, hardware
                //   has entered Sleep, or user walked away. Auto-lock immediately.
                Optional<PrinterEntity> printerOpt = printerRepository.findByIpAddress(ip);
                String seriesName = printerOpt.map(PrinterEntity::getSeriesName).orElse("18 Series");

                boolean shouldLock = false;
                String lockReason = "";

                if (session.isWalkUp()) {
                    if (postJobTimeout) {
                        shouldLock = true;
                        lockReason = "Job completed (15s post-job auto-lock)";
                    } else if (totalSessionSeconds > 25) {
                        shouldLock = true;
                        lockReason = "Walk-up copy session max duration reached (25s idle timeout)";
                    }
                } else {
                    if (secondsSinceHeartbeat > 20 || totalSessionSeconds > 180) {
                        shouldLock = true;
                        lockReason = secondsSinceHeartbeat + "s without heartbeat (EWB foreground idle/sleep)";
                    }
                }

                if (shouldLock) {
                    postJobAutoLockDeadlineByIp.remove(ip);
                    LOGGER.info("[AUTO-LOCK WATCHDOG] Terminal on IP " + ip + " [" + seriesName + "] expired (" +
                            lockReason + ", user: '" + session.username() + "', isWalkUp=" + session.isWalkUp() + "). Auto-locking physical device now.");
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
            if (activeTerminalUsers.containsKey(printer.getIpAddress())) {
                continue; // Skip actively operated printer so user operations have 100% bandwidth
            }
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
            return;
        }
        String ip = printer.getIpAddress().trim();
        String adminUser = (printer.getAdminUsername() != null && !printer.getAdminUsername().isBlank())
                ? printer.getAdminUsername().trim() : "admin";
        String adminPass = (printer.getAdminPassword() != null && !printer.getAdminPassword().isBlank())
                ? printer.getAdminPassword().trim() : "123456";

        try {
            // 1. Query PrintLogs via ToshibaEwbService thread-safe session
            String printLogXml = toshibaEwbService.retrieveJobLogs(ip, adminUser, adminPass, "PrintLog");
            if (printLogXml != null && !printLogXml.isBlank()) {
                parseAndRecordToshibaLogRecords(printLogXml, printer);
            }

            // 2. Query ScanLogs via ToshibaEwbService thread-safe session
            String scanLogXml = toshibaEwbService.retrieveJobLogs(ip, adminUser, adminPass, "ScanLog");
            if (scanLogXml != null && !scanLogXml.isBlank()) {
                parseAndRecordToshibaLogRecords(scanLogXml, printer);
            }
        } catch (Exception e) {
            LOGGER.fine("[COPY POLL] Exception polling " + ip + ": " + e.getMessage());
        }
    }
    private void parseAndRecordToshibaLogRecords(String xml, PrinterEntity printer) {
        Pattern recordPattern = Pattern.compile("<LogRecord>(.*?)</LogRecord>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
        Matcher matcher = recordPattern.matcher(xml);

        int copyCount = 0;
        int scanCount = 0;
        while (matcher.find()) {
            String recordXml = matcher.group(1);

            String jobType = extractTag(recordXml, "PrintJobType", "ScanJobType", "JobType");
            String recordNumber = extractTag(recordXml, "RecordNumber");
            String rawUser = extractTag(recordXml, "UserName");
            String completedTimeStr = extractTag(recordXml, "CompletedTime");
            String pagesStr = extractTag(recordXml, "InputValidPages", "OriginalPages", "Pages");
            String setsStr = extractTag(recordXml, "PrintedSets");
            String docName = extractTag(recordXml, "DocumentName");
            String destination = extractTag(recordXml, "Destination", "Recipient", "EmailAddress");
            String colorMode = extractTag(recordXml, "ColorMode");

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

                        // Skip already recorded jobs immediately to prevent duplicate attribution overhead
            if (recordNumber != null && !recordNumber.isBlank()) {
                String scanFileName = "TOSHIBA-SCAN-" + recordNumber + ".pdf";
                if (copyJobRepository.existsByPrinterIpAndPrinterJobId(printer.getIpAddress(), recordNumber)
                        || scanJobRepository.existsByPrinterIpAndFileName(printer.getIpAddress(), recordNumber)
                        || scanJobRepository.existsByPrinterIpAndFileName(printer.getIpAddress(), scanFileName)) {
                    continue;
                }
            }

            // If Toshiba UserName is blank, resolve effective user via session attribution window
            String effectiveUser = rawUser;
            if (effectiveUser == null || effectiveUser.isBlank()) {
                effectiveUser = resolveTerminalUserForJob(printer.getIpAddress(), completedAt);
            }
            if (effectiveUser == null || effectiveUser.isBlank()) {
                effectiveUser = "walkup";
            }

            if (jobType != null && "Copy".equalsIgnoreCase(jobType.trim())) {
                CopyJobEntity cj = recordCopyJob(effectiveUser, printer.getIpAddress(), printer.getName(), totalPages, 0, totalPages, recordNumber, completedAt);
                if (cj != null) {
                    copyCount++;
                    TerminalSession activeSession = activeTerminalUsers.get(printer.getIpAddress());
                    if (activeSession != null && activeSession.isWalkUp()) {
                        postJobAutoLockDeadlineByIp.put(printer.getIpAddress(), System.currentTimeMillis() + 15000L);
                        LOGGER.info("[AUTO-LOCK] New physical copy job completed on " + printer.getIpAddress() + ". Auto-lock scheduled in 15s.");
                    }
                }
            } else if ((jobType != null && (jobType.toLowerCase().contains("scan") || jobType.toLowerCase().contains("email") || jobType.toLowerCase().contains("efiling")))
                    || recordXml.contains("<Scan>") || recordXml.contains("ScanJobType")) {
                ScanJobEntity sj = recordNativeScanJob(effectiveUser, printer.getIpAddress(), printer.getName(), totalPages, colorMode, docName, destination, recordNumber, completedAt);
                if (sj != null) {
                    scanCount++;
                    TerminalSession activeSession = activeTerminalUsers.get(printer.getIpAddress());
                    if (activeSession != null && activeSession.isWalkUp()) {
                        postJobAutoLockDeadlineByIp.put(printer.getIpAddress(), System.currentTimeMillis() + 15000L);
                        LOGGER.info("[AUTO-LOCK] New physical scan job completed on " + printer.getIpAddress() + ". Auto-lock scheduled in 15s.");
                    }
                }
            }
        }
        if (copyCount > 0 || scanCount > 0) {
            LOGGER.info("[DEVICE POLL] Successfully parsed and recorded " + copyCount + " NEW copy and " + scanCount + " NEW scan records from " + printer.getName() + " (" + printer.getIpAddress() + ")");
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
