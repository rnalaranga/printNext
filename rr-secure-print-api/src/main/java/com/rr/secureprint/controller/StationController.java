package com.rr.secureprint.controller;

import com.rr.secureprint.entity.PrinterEntity;
import com.rr.secureprint.entity.UserEntity;
import com.rr.secureprint.repository.PrinterRepository;
import com.rr.secureprint.repository.UserRepository;
import com.rr.secureprint.service.AuditService;
import com.rr.secureprint.service.AuthService;
import com.rr.secureprint.service.CopyTrackingService;
import com.rr.secureprint.service.ToshibaEwbService;
import com.rr.secureprint.service.SystemSettingsService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/api/station")
public class StationController {

    private static final Logger log = LoggerFactory.getLogger(StationController.class);

    private final PrinterRepository printerRepository;
    private final UserRepository userRepository;
    private final AuthService authService;
    private final AuditService auditService;
    private final CopyTrackingService copyTrackingService;
    private final ToshibaEwbService toshibaEwbService;
    private final SystemSettingsService systemSettingsService;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.rr.secureprint.service.SnmpMonitoringService snmpMonitoringService;
    private final Set<String> unlockedPrinters = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final java.util.Map<String, String> currentAddressBookUserByPrinter = new java.util.concurrent.ConcurrentHashMap<>();

    public StationController(PrinterRepository printerRepository,
                             UserRepository userRepository,
                             AuthService authService,
                             AuditService auditService,
                             CopyTrackingService copyTrackingService,
                             ToshibaEwbService toshibaEwbService,
                             SystemSettingsService systemSettingsService) {
        this.printerRepository = printerRepository;
        this.userRepository = userRepository;
        this.authService = authService;
        this.auditService = auditService;
        this.copyTrackingService = copyTrackingService;
        this.toshibaEwbService = toshibaEwbService;
        this.systemSettingsService = systemSettingsService;
    }

    @PostMapping("/diagnostics")
    public ResponseEntity<?> receiveDiagnostics(@RequestBody(required = false) Map<String, Object> body, HttpServletRequest request) {
        String clientIp = request.getRemoteAddr();
        log.info("[EWB DIAGNOSTICS from {}]: {}", clientIp, body);
        if (clientIp != null && toshibaEwbService.hasPendingLock(clientIp) && !copyTrackingService.hasActiveSession(clientIp)) {
            printerRepository.findByIpAddress(clientIp.trim()).ifPresent(p -> {
                if (!"TOSHIBA_OLD".equalsIgnoreCase(p.getDeviceGeneration())) {
                    CompletableFuture.runAsync(() -> {
                        log.info("[PENDING LOCK RECOVERY] Printer {} ({}) woke up and reported diagnostics. Applying pending lock...", p.getPrinterCode(), p.getIpAddress());
                        toshibaEwbService.setDefaultScreen(p.getIpAddress(), p.getAdminUsername(), p.getAdminPassword(), "EWB", false);
                        toshibaEwbService.setCopyRestriction(p.getIpAddress(), p.getAdminUsername(), p.getAdminPassword(), true, true);
                    });
                }
            });
        }
        return ResponseEntity.ok(Map.of("received", true));
    }

    @RequestMapping(value = "/reset-kiosk", method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<?> resetAllPrintersToEwbKiosk() {
        List<PrinterEntity> printers = printerRepository.findAll().stream()
                .filter(p -> p.isActive() && !"VIRTUAL-QUEUE".equalsIgnoreCase(p.getPrinterCode()))
                .toList();

        Map<String, Object> results = new HashMap<>();
        for (PrinterEntity p : printers) {
            if (!"TOSHIBA_OLD".equalsIgnoreCase(p.getDeviceGeneration())) {
                boolean ok = toshibaEwbService.resetPrinterToEwbKiosk(p.getIpAddress(), p.getAdminUsername(), p.getAdminPassword());
                results.put(p.getPrinterCode() + " (" + p.getIpAddress() + ")", ok ? "RESET_SUCCESS" : "RESET_FAILED");
            }
        }
        return ResponseEntity.ok(Map.of("status", "COMPLETED", "results", results));
    }

    /**
     * Resolves the physical MFP release station for the current EWB terminal session.
     * Identifies the station via:
     * 1. URL parameter (?printer=PCT0001)
     * 2. Physical device HTTP client IP address matching printer.ipAddress
     * 3. Fallback to first active physical printer (for desktop simulation/browser testing)
     */
    @GetMapping("/current")
    public ResponseEntity<?> getCurrentStation(
            @RequestParam(value = "printer", required = false) String printerParam,
            HttpServletRequest request) {

        String clientIp = request.getRemoteAddr();
        List<PrinterEntity> allPhysical = printerRepository.findAll().stream()
                .filter(p -> p.isActive() && !"VIRTUAL-QUEUE".equalsIgnoreCase(p.getPrinterCode()))
                .toList();

        PrinterEntity matched = null;

        // 1. By explicit printer code param
        if (printerParam != null && !printerParam.isBlank() && !"VIRTUAL-QUEUE".equalsIgnoreCase(printerParam)) {
            matched = printerRepository.findByPrinterCode(printerParam.trim().toUpperCase()).orElse(null);
        }

        // 2. By physical MFP client IP address
        if (matched == null && clientIp != null && !clientIp.isBlank()) {
            String cleanIp = clientIp.replace("/", "").trim();
            for (PrinterEntity p : allPhysical) {
                if (cleanIp.equalsIgnoreCase(p.getIpAddress()) || cleanIp.endsWith(p.getIpAddress())) {
                    matched = p;
                    break;
                }
            }
        }

        // 3. Fallback to first physical printer
        if (matched == null && !allPhysical.isEmpty()) {
            matched = allPhysical.getFirst();
        }

        if (clientIp != null && toshibaEwbService.hasPendingLock(clientIp) && !copyTrackingService.hasActiveSession(clientIp)) {
            printerRepository.findByIpAddress(clientIp.trim()).ifPresent(p -> {
                if (!"TOSHIBA_OLD".equalsIgnoreCase(p.getDeviceGeneration())) {
                    CompletableFuture.runAsync(() -> {
                        log.info("[PENDING LOCK RECOVERY] Printer {} ({}) accessed /current. Applying pending lock...", p.getPrinterCode(), p.getIpAddress());
                        toshibaEwbService.setDefaultScreen(p.getIpAddress(), p.getAdminUsername(), p.getAdminPassword(), "EWB", false);
                        toshibaEwbService.setCopyRestriction(p.getIpAddress(), p.getAdminUsername(), p.getAdminPassword(), true, true);
                    });
                }
            });
        }

        boolean paperEmpty = false;
        String paperEmptyMessage = "";
        int tonerLevel = -1;
        if (matched != null && snmpMonitoringService != null) {
            try {
                com.rr.secureprint.service.SnmpMonitoringService.HardwareReadiness r1 =
                        snmpMonitoringService.checkPrinterHardwareReadiness(matched, "DRAWER1");
                com.rr.secureprint.service.SnmpMonitoringService.HardwareReadiness r2 =
                        snmpMonitoringService.checkPrinterHardwareReadiness(matched, "DRAWER2");
                boolean d1Empty = !r1.isReady() && r1.reason() != null && r1.reason().toLowerCase().contains("paper");
                boolean d2Empty = !r2.isReady() && r2.reason() != null && r2.reason().toLowerCase().contains("paper");
                if (d1Empty && d2Empty) {
                    paperEmpty = true;
                    paperEmptyMessage = "Drawer 1 (A4) & Drawer 2 (A3) — Paper Empty";
                } else if (d1Empty) {
                    paperEmpty = true;
                    paperEmptyMessage = "Drawer 1 (A4) — Paper Empty";
                } else if (d2Empty) {
                    paperEmpty = true;
                    paperEmptyMessage = "Drawer 2 (A3) — Paper Empty";
                }
            } catch (Exception ignored) {}
            try {
                com.rr.secureprint.service.SnmpClient.SnmpItem cur = com.rr.secureprint.service.SnmpClient.query(
                        matched.getIpAddress(), "public", "1.3.6.1.2.1.43.11.1.1.9.1.1", false, 500);
                com.rr.secureprint.service.SnmpClient.SnmpItem max = com.rr.secureprint.service.SnmpClient.query(
                        matched.getIpAddress(), "public", "1.3.6.1.2.1.43.11.1.1.8.1.1", false, 500);
                if (cur != null && max != null && cur.getValue() != null && max.getValue() != null) {
                    int c = Integer.parseInt(cur.getValue().trim());
                    int m = Integer.parseInt(max.getValue().trim());
                    if (m > 0 && c >= 0) {
                        tonerLevel = Math.min(100, Math.max(0, (int) Math.round((c * 100.0) / m)));
                    }
                }
            } catch (Exception ignored) {}
        }

        java.util.Map<String, Object> resp = new java.util.HashMap<>();
        resp.put("currentStation", matched != null ? matched : Map.of(
                "printerCode", "STATION-01",
                "name", "Toshiba e-STUDIO MFP",
                "ipAddress", "192.168.1.11",
                "rawPort", 9100,
                "location", "Main Office"
        ));
        resp.put("allStations", allPhysical);
        resp.put("virtualQueueName", "RR Secure Virtual Queue");
        resp.put("virtualQueueShare", "\\\\SERVER\\RR-Secure-Print");
        resp.put("clientIp", clientIp != null ? clientIp : "");
        resp.put("branding", systemSettingsService.getPublicBranding());
        resp.put("paperEmpty", paperEmpty);
        resp.put("paperEmptyMessage", paperEmptyMessage);
        resp.put("tonerLevel", tonerLevel);
        return ResponseEntity.ok(resp);
    }

    /**
     * Called when a user taps "Copy / Scan (Home)" to transition from nexPrint EWB
     * to native Toshiba copy/scan functions. Ensures session attribution is registered.
     */
    @PostMapping("/device-functions")
    public ResponseEntity<?> openDeviceFunctions(
            @RequestBody(required = false) Map<String, Object> body,
            HttpServletRequest request) {
        String printerCode = (body != null && body.get("printerCode") != null) ? body.get("printerCode").toString() : null;
        if (printerCode == null && body != null && body.get("terminalId") != null) {
            printerCode = body.get("terminalId").toString();
        }
        String printerIp = (body != null && body.get("printerIp") != null) ? body.get("printerIp").toString() : null;
        String mode = (body != null && body.get("mode") != null) ? body.get("mode").toString().toUpperCase() : "COPY";
        boolean switchScreen = body == null || !body.containsKey("switchScreen") ||
                "true".equalsIgnoreCase(String.valueOf(body.get("switchScreen")));
        String clientIp = request.getRemoteAddr();

        Optional<UserEntity> resolvedUserOpt = authService.getCurrentUser(request);
        if (resolvedUserOpt.isEmpty() && body != null && body.get("username") != null && !body.get("username").toString().isBlank()) {
            resolvedUserOpt = userRepository.findByUsernameIgnoreCase(body.get("username").toString().trim());
        }
        final Optional<UserEntity> userOpt = resolvedUserOpt;
        final String username = userOpt.map(UserEntity::getUsername).orElse("unknown");

        Optional<PrinterEntity> printerOpt = Optional.empty();
        if (printerCode != null && !printerCode.isBlank()) {
            printerOpt = printerRepository.findByPrinterCode(printerCode.trim().toUpperCase());
        }
        if (printerOpt.isEmpty() && printerIp != null && !printerIp.isBlank()) {
            printerOpt = printerRepository.findByIpAddress(printerIp.trim());
        }
        if (printerOpt.isEmpty() && clientIp != null) {
            printerOpt = printerRepository.findByIpAddress(clientIp.trim());
        }

        boolean[] screenSuccess = { false };
        printerOpt.ifPresent(p -> {
            try {
                request.getSession(true).setAttribute("RR_ACTIVE_PRINTER_IP", p.getIpAddress());
            } catch (Exception ignored) {}
            // Unlock copy/scan/print functions and set target DefaultScreen for authenticated walk-up session
            if (!"TOSHIBA_OLD".equalsIgnoreCase(p.getDeviceGeneration())) {
                try {
                    String targetScreen = "SCAN".equalsIgnoreCase(mode) ? "Scan"
                            : (("USB".equalsIgnoreCase(mode) || "PRINT".equalsIgnoreCase(mode)) ? "Print"
                            : ("HOME".equalsIgnoreCase(mode) ? "Home" : "Copy"));
                    boolean unlockOk = switchScreen
                            ? toshibaEwbService.unlockCopyAndSetDefaultScreen(p.getIpAddress(), p.getAdminUsername(), p.getAdminPassword(), targetScreen)
                            : toshibaEwbService.setCopyRestriction(p.getIpAddress(), p.getAdminUsername(), p.getAdminPassword(), false, true);
                    if (!unlockOk && switchScreen) {
                        unlockOk = toshibaEwbService.setCopyRestriction(p.getIpAddress(), p.getAdminUsername(), p.getAdminPassword(), false, true);
                    }
                    if (unlockOk) {
                        unlockedPrinters.add(p.getIpAddress());
                        screenSuccess[0] = true;
                        copyTrackingService.registerWalkUpSession(p.getIpAddress(), username);
                        log.info("[STATION CONTROLLER] Device function unlocked & DefaultScreen={} on {}. Physical engine is open.",
                                targetScreen, p.getIpAddress());
                        if (switchScreen && !"EWB".equalsIgnoreCase(targetScreen)) {
                            final String ip = p.getIpAddress();
                            final String admUser = p.getAdminUsername();
                            final String admPass = p.getAdminPassword();
                            CompletableFuture.delayedExecutor(18, java.util.concurrent.TimeUnit.SECONDS).execute(() -> {
                                try {
                                    boolean restored = toshibaEwbService.setDefaultScreen(ip, admUser, admPass, "EWB");
                                    log.info("[STATION CONTROLLER] Pre-restored DefaultScreen=EWB on {} after 18s walk-up transition: {}", ip, restored);
                                } catch (Exception ex) {
                                    log.debug("[STATION CONTROLLER] Pre-restore DefaultScreen=EWB error on {}: {}", ip, ex.getMessage());
                                }
                            });
                        }
                    } else {
                        log.error("[STATION CONTROLLER] Failed to unlock device functions on {}", p.getIpAddress());
                    }
                } catch (Exception ex) {
                    screenSuccess[0] = false;
                    log.warn("[STATION CONTROLLER] Synchronous unlock error on {}: {}", p.getIpAddress(), ex.getMessage());
                }

                // Asynchronously synchronize address book for scan
                if ("SCAN".equalsIgnoreCase(mode)) {
                    CompletableFuture.runAsync(() -> {
                        try {
                            String email = userOpt.map(UserEntity::getEmail).filter(e -> !e.isBlank()).orElse(null);
                            if (email == null) {
                                email = username + "@rrconstruction.lk";
                                userOpt.ifPresent(u -> {
                                    u.setEmail(username + "@rrconstruction.lk");
                                    userRepository.save(u);
                                });
                            }
                            String dispName = userOpt.map(UserEntity::getDisplayName).filter(d -> !d.isBlank()).orElse(username);
                            String dept = userOpt.map(UserEntity::getDepartment).orElse("");

                            boolean fromOk = toshibaEwbService.setScanFromAddress(
                                    p.getIpAddress(), p.getAdminUsername(), p.getAdminPassword(),
                                    email, dispName
                            );
                            log.info("[STATION CONTROLLER] Pushed Scan FROM address {} ({}) to {}: {}", dispName, email, p.getIpAddress(), fromOk);

                            String lastUser = currentAddressBookUserByPrinter.get(p.getIpAddress());
                            if (!username.equalsIgnoreCase(lastUser)) {
                                currentAddressBookUserByPrinter.put(p.getIpAddress(), username);
                                boolean addrOk = toshibaEwbService.registerAddressBookContact(
                                        p.getIpAddress(), p.getAdminUsername(), p.getAdminPassword(),
                                        username, dispName, email, dept
                                );
                                log.info("[STATION CONTROLLER] Synchronized sole user {} ({}) to Toshiba AddressBook on {}: {}",
                                        dispName, email, p.getIpAddress(), addrOk);
                            }
                        } catch (Exception ex) {
                            log.debug("Async contact push error on {}: {}", p.getIpAddress(), ex.getMessage());
                        }
                    });
                }
            }
            auditService.logEvent("SWITCH_TO_DEVICE_FUNCTIONS",
                    userOpt.map(UserEntity::getUserSid).orElse("SYSTEM"),
                    username, null, p.getPrinterCode(), p.getIpAddress(),
                    "User switched to Native Toshiba Device Functions (" + mode + ", switchScreen=" + switchScreen + ")", "SUCCESS");
        });

        String msg;
        if (screenSuccess[0]) {
            msg = (mode.equalsIgnoreCase("SCAN") ? "Document scanner functions unlocked for " : "Walk-up photocopy functions unlocked for ") + username + ".";
        } else {
            msg = "Could not unlock " + mode.toLowerCase() + " functions on MFP. Please try again.";
        }
        return ResponseEntity.ok(Map.of(
                "success", screenSuccess[0],
                "mode", mode,
                "screenSwitched", screenSuccess[0],
                "message", msg
        ));
    }

    /**
     * Called when the physical copy screen opens (EWB browser minimized/hidden).
     * Immediately pre-restores DefaultScreen to 'EWB' via CWS so that when the user
     * presses the physical [SLEEP] button or [C], the machine is guaranteed to lock
     * and wake directly into the nexPrint PIN screen.
     */
    @PostMapping("/copy-screen-opened")
    public ResponseEntity<?> copyScreenOpened(
            @RequestBody(required = false) Map<String, Object> body,
            HttpServletRequest request) {
        String printerCode = (body != null && body.get("printerCode") != null) ? body.get("printerCode").toString() : null;
        if (printerCode == null && body != null && body.get("terminalId") != null) {
            printerCode = body.get("terminalId").toString();
        }
        String clientIp = request.getRemoteAddr();

        Optional<PrinterEntity> printerOpt = Optional.empty();
        if (printerCode != null && !printerCode.isBlank()) {
            printerOpt = printerRepository.findByPrinterCode(printerCode.trim().toUpperCase());
        }
        if (printerOpt.isEmpty() && clientIp != null) {
            printerOpt = printerRepository.findByIpAddress(clientIp.trim());
        }
        if (printerOpt.isEmpty()) {
            HttpSession session = request.getSession(false);
            if (session != null && session.getAttribute("RR_ACTIVE_PRINTER_IP") != null) {
                printerOpt = printerRepository.findByIpAddress(session.getAttribute("RR_ACTIVE_PRINTER_IP").toString());
            }
        }

        printerOpt.ifPresent(p -> {
            log.info("[STATION CONTROLLER] Native copy/scan screen active on {} - pre-restoring DefaultScreen=EWB", p.getIpAddress());
            if (!"TOSHIBA_OLD".equalsIgnoreCase(p.getDeviceGeneration())) {
                CompletableFuture.runAsync(() -> {
                    try {
                        toshibaEwbService.setDefaultScreen(p.getIpAddress(), p.getAdminUsername(), p.getAdminPassword(), "EWB", false);
                    } catch (Exception ex) {
                        log.debug("Could not pre-restore DefaultScreen=EWB on {}: {}", p.getIpAddress(), ex.getMessage());
                    }
                });
            }
        });

        return ResponseEntity.ok(Map.of("success", true));
    }

    /**
     * Restores the default screen on the Toshiba MFP back to EWB
     * (e.g. when user returns to main apps menu from copy/scan view).
     */
    @PostMapping("/restore-screen")
    public ResponseEntity<?> restoreScreen(
            @RequestBody(required = false) Map<String, Object> body,
            HttpServletRequest request) {
        String printerCode = (body != null && body.get("printerCode") != null) ? body.get("printerCode").toString() : null;
        String printerIp = (body != null && body.get("printerIp") != null) ? body.get("printerIp").toString() : null;
        String clientIp = request.getRemoteAddr();

        Optional<PrinterEntity> printerOpt = Optional.empty();
        if (printerCode != null && !printerCode.isBlank()) {
            printerOpt = printerRepository.findByPrinterCode(printerCode.trim().toUpperCase());
        }
        if (printerOpt.isEmpty() && printerIp != null && !printerIp.isBlank()) {
            printerOpt = printerRepository.findByIpAddress(printerIp.trim());
        }
        if (printerOpt.isEmpty() && clientIp != null) {
            printerOpt = printerRepository.findByIpAddress(clientIp.trim());
        }

        printerOpt.ifPresent(p -> {
            try {
                toshibaEwbService.setDefaultScreen(p.getIpAddress(), p.getAdminUsername(), p.getAdminPassword(), "EWB", true);
                log.info("[STATION CONTROLLER] DefaultScreen restored to 'EWB' on {}", p.getIpAddress());
            } catch (Exception ex) {
                log.warn("Could not restore DefaultScreen to EWB on {}: {}", p.getIpAddress(), ex.getMessage());
            }
        });

        return ResponseEntity.ok(Map.of("success", true, "screen", "EWB"));
    }

    /**
     * Executes an authenticated photocopy job directly from the Touchscreen Copy Console.
     * Records copy count to account and logs audit event.
     */
    @PostMapping("/execute-copy")
    public ResponseEntity<?> executeCopy(
            @RequestBody(required = false) Map<String, Object> body,
            HttpServletRequest request) {
        Optional<UserEntity> userOpt = authService.getCurrentUser(request);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of("success", false, "message", "Unauthorized. Please log in."));
        }
        UserEntity user = userOpt.get();

        int copies = 1;
        if (body != null && body.containsKey("copies")) {
            try { copies = Integer.parseInt(body.get("copies").toString()); } catch (Exception ignored) {}
        }
        boolean color = body != null && Boolean.parseBoolean(String.valueOf(body.getOrDefault("color", false)));
        boolean duplex = body != null && Boolean.parseBoolean(String.valueOf(body.getOrDefault("duplex", false)));
        String paperSize = body != null && body.containsKey("paperSize") ? String.valueOf(body.get("paperSize")) : "A4";

        String clientIp = request.getRemoteAddr();
        String printerIp = clientIp != null ? clientIp : "127.0.0.1";
        String printerName = "Toshiba e-STUDIO MFP";
        String printerCode = "STATION-01";

        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute("RR_ACTIVE_PRINTER_IP") != null) {
            printerIp = session.getAttribute("RR_ACTIVE_PRINTER_IP").toString();
        }

        Optional<PrinterEntity> printerOpt = printerRepository.findByIpAddress(printerIp);
        if (printerOpt.isPresent()) {
            printerName = printerOpt.get().getName();
            printerCode = printerOpt.get().getPrinterCode();
        }

        // Record the copy job in copy tracking service
        int colorPages = color ? copies : 0;
        int monoPages = color ? 0 : copies;
        String jobId = "COPY-" + System.currentTimeMillis();
        copyTrackingService.recordCopyJob(user.getUsername(), printerIp, printerName, copies, colorPages, monoPages, jobId, Instant.now());

        // Refresh user copy count from database
        long currentCopyCount = userRepository.findByUsernameIgnoreCase(user.getUsername())
                .map(UserEntity::getCopyCount).orElse((long) copies);

        auditService.logEvent("PHOTOCOPY_COMPLETED", user.getUserSid(), user.getUsername(), null, printerCode, printerIp,
                "Photocopy produced: " + copies + " page(s), color=" + color + ", duplex=" + duplex + ", size=" + paperSize, "SUCCESS");

        return ResponseEntity.ok(Map.of(
                "success", true,
                "copies", copies,
                "color", color,
                "duplex", duplex,
                "paperSize", paperSize,
                "totalUserCopyCount", currentCopyCount,
                "message", "Successfully copied " + copies + " page(s) on " + printerName
        ));
    }

    /**
     * Executes an authenticated document scan job directly from the Touchscreen Scan Console.
     */
    @PostMapping("/execute-scan")
    public ResponseEntity<?> executeScan(
            @RequestBody(required = false) Map<String, Object> body,
            HttpServletRequest request) {
        Optional<UserEntity> userOpt = authService.getCurrentUser(request);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of("success", false, "message", "Unauthorized. Please log in."));
        }
        UserEntity user = userOpt.get();

        int pages = 1;
        if (body != null && body.containsKey("pages")) {
            try { pages = Integer.parseInt(body.get("pages").toString()); } catch (Exception ignored) {}
        }
        String dest = body != null && body.containsKey("destination") ? String.valueOf(body.get("destination")) : "EMAIL";
        String resolution = body != null && body.containsKey("resolution") ? String.valueOf(body.get("resolution")) : "300dpi";

        String clientIp = request.getRemoteAddr();
        String printerIp = clientIp != null ? clientIp : "127.0.0.1";
        String printerName = "Toshiba e-STUDIO MFP";
        String printerCode = "STATION-01";

        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute("RR_ACTIVE_PRINTER_IP") != null) {
            printerIp = session.getAttribute("RR_ACTIVE_PRINTER_IP").toString();
        }

        Optional<PrinterEntity> printerOpt = printerRepository.findByIpAddress(printerIp);
        if (printerOpt.isPresent()) {
            printerName = printerOpt.get().getName();
            printerCode = printerOpt.get().getPrinterCode();
        }

        String colorMode = body != null && body.containsKey("colorMode") ? String.valueOf(body.get("colorMode")) : "COLOR";

        // Record native scan job in tracking service
        String jobId = "SCAN-" + System.currentTimeMillis();
        copyTrackingService.recordNativeScanJob(user.getUsername(), printerIp, printerName, pages, colorMode, "Document Scan", dest, jobId, java.time.Instant.now());

        // Refresh user scan count from database
        long currentScanCount = userRepository.findByUsernameIgnoreCase(user.getUsername())
                .map(UserEntity::getScanCount).orElse((long) pages);

        auditService.logEvent("DOCUMENT_SCAN_COMPLETED", user.getUserSid(), user.getUsername(), null, printerCode, printerIp,
                "Scan dispatched: " + pages + " page(s), destination=" + dest + ", resolution=" + resolution, "SUCCESS");

        return ResponseEntity.ok(Map.of(
                "success", true,
                "pages", pages,
                "destination", dest,
                "resolution", resolution,
                "totalUserScanCount", currentScanCount,
                "message", "Successfully scanned " + pages + " page(s) on " + printerName
        ));
    }

    /**
     * Emergency / Hardware Sleep Auto-Lock:
     * Immediately locks walk-up copy restrictions on the physical Toshiba MFP via CWS
     * and clears active terminal/web sessions.
     */
    @PostMapping("/auto-lock")
    public ResponseEntity<?> autoLockDevice(
            @RequestBody(required = false) Map<String, Object> body,
            HttpServletRequest request) {
        String printerCode = (body != null && body.get("printerCode") != null) ? body.get("printerCode").toString() : null;
        if (printerCode == null && body != null && body.get("terminalId") != null) {
            printerCode = body.get("terminalId").toString();
        }
        String printerIp = (body != null && body.get("printerIp") != null) ? body.get("printerIp").toString() : null;
        String clientIp = request.getRemoteAddr();
        String reason = (body != null && body.get("reason") != null) ? body.get("reason").toString() : "SLEEP_MODE";
        log.info("[AUTO-LOCK] Auto-lock request received: printerCode={}, printerIp={}, reason={}, clientIp={}", printerCode, printerIp, reason, clientIp);

        List<PrinterEntity> toLock = new ArrayList<>();

        if (printerCode != null && !printerCode.isBlank()) {
            printerRepository.findByPrinterCode(printerCode.trim().toUpperCase()).ifPresent(toLock::add);
        }
        if (toLock.isEmpty() && printerIp != null && !printerIp.isBlank()) {
            printerRepository.findByIpAddress(printerIp.trim()).ifPresent(toLock::add);
        }
        if (toLock.isEmpty() && clientIp != null) {
            printerRepository.findByIpAddress(clientIp.trim()).ifPresent(toLock::add);
        }
        HttpSession session = request.getSession(false);
        if (toLock.isEmpty() && session != null) {
            Object sessIp = session.getAttribute("RR_ACTIVE_PRINTER_IP");
            if (sessIp != null) {
                printerRepository.findByIpAddress(sessIp.toString()).ifPresent(toLock::add);
            }
        }

        // If specific printer was not resolved from request, lock all printers currently in active copy sessions or unlocked set
        if (toLock.isEmpty()) {
            for (String ip : copyTrackingService.getActiveTerminalIps()) {
                printerRepository.findByIpAddress(ip).ifPresent(toLock::add);
            }
            for (String ip : unlockedPrinters) {
                printerRepository.findByIpAddress(ip).ifPresent(toLock::add);
            }
        }

        for (PrinterEntity p : toLock) {
            unlockedPrinters.remove(p.getIpAddress());
            currentAddressBookUserByPrinter.remove(p.getIpAddress());
            copyTrackingService.recordTerminalLogout(p.getIpAddress());
            log.info("[STATION CONTROLLER] Auto-lock dispatched via recordTerminalLogout on {}", p.getIpAddress());
            auditService.logEvent("DEVICE_AUTO_LOCKED", "SYSTEM", "hardware", null,
                    p.getPrinterCode(), p.getIpAddress(),
                    "Physical device copy restriction locked due to " + reason, "SUCCESS");
        }

        // Invalidate web session without re-triggering duplicate recordTerminalLogout calls
        try {
            HttpSession existingSession = request.getSession(false);
            if (existingSession != null) {
                existingSession.invalidate();
            }
        } catch (Exception ignored) {}

        return ResponseEntity.ok(Map.of("success", true, "locked", true, "lockedPrinters", toLock.size(), "reason", reason));
    }

    /**
     * Heartbeat from active touch terminal:
     * Keeps the session alive. If heartbeats stop for > 20s (e.g. user pressed physical Sleep button),
     * the server watchdog will automatically re-lock the physical MFP.
     */
    @PostMapping("/heartbeat")
    public ResponseEntity<?> terminalHeartbeat(
            @RequestBody(required = false) Map<String, String> body,
            HttpServletRequest request) {
        String printerCode = body != null ? body.get("printerCode") : null;
        String clientIp = request.getRemoteAddr();

        Optional<PrinterEntity> printerOpt = Optional.empty();
        if (printerCode != null && !printerCode.isBlank()) {
            printerOpt = printerRepository.findByPrinterCode(printerCode.trim().toUpperCase());
        }
        if (printerOpt.isEmpty() && clientIp != null) {
            printerOpt = printerRepository.findByIpAddress(clientIp.trim());
        }
        if (printerOpt.isEmpty()) {
            HttpSession session = request.getSession(false);
            if (session != null) {
                Object sessIp = session.getAttribute("RR_ACTIVE_PRINTER_IP");
                if (sessIp != null) {
                    printerOpt = printerRepository.findByIpAddress(sessIp.toString());
                }
            }
        }
        if (printerOpt.isPresent()) {
            PrinterEntity p = printerOpt.get();
            copyTrackingService.updateSessionHeartbeat(p.getIpAddress());
            String activeUser = copyTrackingService.getActiveTerminalUser(p.getIpAddress());
            boolean hasActiveSession = (activeUser != null);
            boolean checkBusy = body != null && "true".equalsIgnoreCase(body.get("checkBusy"));
            boolean printerBusy = hasActiveSession && checkBusy && copyTrackingService.isPrinterHardwareBusy(p.getIpAddress());
            if (printerBusy) {
                copyTrackingService.extendWalkUpActivity(p.getIpAddress());
            }
            return ResponseEntity.ok(Map.of(
                    "status", "alive",
                    "authenticated", hasActiveSession,
                    "printerBusy", printerBusy,
                    "printer", p.getPrinterCode()
            ));
        }

        return ResponseEntity.ok(Map.of("status", "alive", "authenticated", false, "printerBusy", false));
    }

    /**
     * Public branding endpoint for touch terminals and authentication displays.
     */
    @GetMapping("/branding")
    public ResponseEntity<?> getBranding() {
        return ResponseEntity.ok(systemSettingsService.getPublicBranding());
    }
}
