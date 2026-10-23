package com.rr.secureprint.controller;

import com.rr.secureprint.entity.PrinterEntity;
import com.rr.secureprint.entity.UserEntity;
import com.rr.secureprint.repository.PrinterRepository;
import com.rr.secureprint.service.AuditService;
import com.rr.secureprint.service.AuthService;
import com.rr.secureprint.service.CopyTrackingService;
import com.rr.secureprint.service.ToshibaEwbService;
import com.rr.secureprint.service.SystemSettingsService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import jakarta.servlet.http.HttpSession;

@RestController
@RequestMapping("/api/station")
public class StationController {

    private final PrinterRepository printerRepository;
    private final AuthService authService;
    private final AuditService auditService;
    private final CopyTrackingService copyTrackingService;
    private final ToshibaEwbService toshibaEwbService;
    private final SystemSettingsService systemSettingsService;

    public StationController(PrinterRepository printerRepository,
                             AuthService authService,
                             AuditService auditService,
                             CopyTrackingService copyTrackingService,
                             ToshibaEwbService toshibaEwbService,
                             SystemSettingsService systemSettingsService) {
        this.printerRepository = printerRepository;
        this.authService = authService;
        this.auditService = auditService;
        this.copyTrackingService = copyTrackingService;
        this.toshibaEwbService = toshibaEwbService;
        this.systemSettingsService = systemSettingsService;
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

        return ResponseEntity.ok(Map.of(
                "currentStation", matched != null ? matched : Map.of(
                        "printerCode", "STATION-01",
                        "name", "Toshiba e-STUDIO MFP",
                        "ipAddress", "192.168.1.11",
                        "rawPort", 9100,
                        "location", "Main Office"
                ),
                "allStations", allPhysical,
                "virtualQueueName", "RR Secure Virtual Queue",
                "virtualQueueShare", "\\\\SERVER\\RR-Secure-Print",
                "clientIp", clientIp != null ? clientIp : "",
                "branding", systemSettingsService.getPublicBranding()
        ));
    }

    /**
     * Called when a user taps "Copy / Scan (Home)" to transition from nexPrint EWB
     * to native Toshiba copy/scan functions. Ensures session attribution is registered.
     */
    @PostMapping("/device-functions")
    public ResponseEntity<?> openDeviceFunctions(
            @RequestBody(required = false) Map<String, String> body,
            HttpServletRequest request) {
        String printerCode = body != null ? body.get("printerCode") : null;
        String clientIp = request.getRemoteAddr();

        Optional<UserEntity> userOpt = authService.getCurrentUser(request);
        String username = userOpt.map(UserEntity::getUsername).orElse("unknown");

        Optional<PrinterEntity> printerOpt = Optional.empty();
        if (printerCode != null && !printerCode.isBlank()) {
            printerOpt = printerRepository.findByPrinterCode(printerCode.trim().toUpperCase());
        }
        if (printerOpt.isEmpty() && clientIp != null) {
            printerOpt = printerRepository.findByIpAddress(clientIp.trim());
        }

        printerOpt.ifPresent(p -> {
            try {
                request.getSession(true).setAttribute("RR_ACTIVE_PRINTER_IP", p.getIpAddress());
            } catch (Exception ignored) {}
            copyTrackingService.registerActiveTerminalUser(p.getIpAddress(), username);
            // Dynamically unlock walk-up copy restriction via TopAccess CWS for modern e-BRIDGE Next MFPs
            if (!"TOSHIBA_OLD".equalsIgnoreCase(p.getDeviceGeneration())) {
                CompletableFuture.runAsync(() -> {
                    try {
                        toshibaEwbService.setCopyRestriction(p.getIpAddress(), p.getAdminUsername(), p.getAdminPassword(), false);
                    } catch (Exception ex) {
                        // non-fatal
                    }
                });
            }
            auditService.logEvent("SWITCH_TO_DEVICE_FUNCTIONS",
                    userOpt.map(UserEntity::getUserSid).orElse("SYSTEM"),
                    username, null, p.getPrinterCode(), p.getIpAddress(),
                    "User switched to Native Toshiba Device Functions (Copy/Scan)", "SUCCESS");
        });

        return ResponseEntity.ok(Map.of(
                "success", true,
                "mode", "DEVICE_FUNCTIONS",
                "message", "Walk-up photocopy functions unlocked for " + username + ". You can now make copies."
        ));
    }

    /**
     * Emergency / Hardware Sleep Auto-Lock:
     * Immediately locks walk-up copy restrictions on the physical Toshiba MFP via CWS
     * and clears active terminal/web sessions.
     */
    @PostMapping("/auto-lock")
    public ResponseEntity<?> autoLockDevice(
            @RequestBody(required = false) Map<String, String> body,
            HttpServletRequest request) {
        String printerCode = body != null ? body.get("printerCode") : null;
        String clientIp = request.getRemoteAddr();
        String reason = body != null ? body.getOrDefault("reason", "SLEEP_MODE") : "SLEEP_MODE";

        List<PrinterEntity> toLock = new ArrayList<>();

        if (printerCode != null && !printerCode.isBlank()) {
            printerRepository.findByPrinterCode(printerCode.trim().toUpperCase()).ifPresent(toLock::add);
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

        // If specific printer was not resolved from request, lock all printers currently in active copy sessions
        if (toLock.isEmpty()) {
            for (String ip : copyTrackingService.getActiveTerminalIps()) {
                printerRepository.findByIpAddress(ip).ifPresent(toLock::add);
            }
        }

        for (PrinterEntity p : toLock) {
            if (!"TOSHIBA_OLD".equalsIgnoreCase(p.getDeviceGeneration())) {
                try {
                    toshibaEwbService.setCopyRestriction(p.getIpAddress(), p.getAdminUsername(), p.getAdminPassword(), true);
                } catch (Exception ex) {
                    // non-fatal
                }
            }
            copyTrackingService.recordTerminalLogout(p.getIpAddress());
            auditService.logEvent("DEVICE_AUTO_LOCKED", "SYSTEM", "hardware", null,
                    p.getPrinterCode(), p.getIpAddress(),
                    "Physical device copy restriction locked due to " + reason, "SUCCESS");
        }

        // Invalidate web session
        try {
            authService.logout(request);
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
        printerOpt.ifPresent(p -> copyTrackingService.updateSessionHeartbeat(p.getIpAddress()));

        return ResponseEntity.ok(Map.of("status", "alive"));
    }

    /**
     * Public branding endpoint for touch terminals and authentication displays.
     */
    @GetMapping("/branding")
    public ResponseEntity<?> getBranding() {
        return ResponseEntity.ok(systemSettingsService.getPublicBranding());
    }
}
