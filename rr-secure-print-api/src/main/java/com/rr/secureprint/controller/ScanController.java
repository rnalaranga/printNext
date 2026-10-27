package com.rr.secureprint.controller;

import com.rr.secureprint.dto.ScanJobDto;
import com.rr.secureprint.dto.ScanRequest;
import com.rr.secureprint.entity.SystemSettingsEntity;
import com.rr.secureprint.entity.UserEntity;
import com.rr.secureprint.service.AuthService;
import com.rr.secureprint.service.ScanService;
import com.rr.secureprint.service.SystemSettingsService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/scan")
public class ScanController {

    private final ScanService scanService;
    private final AuthService authService;
    private final SystemSettingsService systemSettingsService;

    public ScanController(ScanService scanService,
                          AuthService authService,
                          SystemSettingsService systemSettingsService) {
        this.scanService = scanService;
        this.authService = authService;
        this.systemSettingsService = systemSettingsService;
    }

    /**
     * Executes Scan-to-Email for the authenticated touchscreen or mobile user.
     */
    @PostMapping("/email")
    public ResponseEntity<?> scanToEmail(@RequestBody(required = false) ScanRequest request,
                                         HttpServletRequest httpRequest) {
        Optional<UserEntity> userOpt = authService.getCurrentUser(httpRequest);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("success", false, "message", "User session expired or not authenticated."));
        }

        UserEntity user = userOpt.get();
        ScanRequest safeRequest = request != null ? request : new ScanRequest(null, null, null, null, null, 1, "COLOR", "300DPI", "PDF", "SIMPLEX");

        try {
            ScanJobDto result = scanService.processScanToEmail(safeRequest, user, httpRequest.getRemoteAddr());
            return ResponseEntity.ok(Map.of(
                    "success", "SENT".equalsIgnoreCase(result.status()),
                    "job", result,
                    "message", result.deliveryMessage() != null ? result.deliveryMessage() : "Scan delivered successfully."
            ));
        } catch (Exception ex) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Scan error: " + ex.getMessage()
            ));
        }
    }

    /**
     * Retrieves current scan configuration and user defaults for touchscreen display.
     */
    @GetMapping("/config")
    public ResponseEntity<?> getScanConfig(HttpServletRequest httpRequest) {
        Optional<UserEntity> userOpt = authService.getCurrentUser(httpRequest);
        SystemSettingsEntity settings = systemSettingsService.getOrCreateSettings();

        String userEmail = userOpt.map(UserEntity::getEmail).orElse(null);
        String username = userOpt.map(UserEntity::getUsername).orElse(null);
        if (userEmail == null && username != null) {
            userEmail = username + "@rrconstruction.lk";
        }

        Map<String, Object> resp = new HashMap<>();
        resp.put("enabled", !Boolean.FALSE.equals(settings.getScanToEmailEnabled()));
        resp.put("userEmail", userEmail != null ? userEmail : "");
        resp.put("smtpConfigured", settings.getSmtpHost() != null && !settings.getSmtpHost().isBlank());
        resp.put("defaultSubject", settings.getDefaultScanSubject() != null ? settings.getDefaultScanSubject() : "Scanned Document from {printer}");
        resp.put("availableColorModes", List.of("COLOR", "GRAYSCALE", "MONO"));
        resp.put("availableResolutions", List.of("200DPI", "300DPI", "600DPI"));
        resp.put("availableFormats", List.of("PDF", "TIFF", "JPEG"));

        return ResponseEntity.ok(resp);
    }

    /**
     * Returns personal scan history for the logged-in user.
     */
    @GetMapping("/history")
    public ResponseEntity<List<ScanJobDto>> getUserScanHistory(HttpServletRequest httpRequest) {
        Optional<UserEntity> userOpt = authService.getCurrentUser(httpRequest);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(scanService.getUserScans(userOpt.get().getUsername()));
    }
}
