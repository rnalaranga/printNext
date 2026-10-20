package com.rr.secureprint.controller;

import com.rr.secureprint.dto.*;
import com.rr.secureprint.entity.PrinterEntity;
import com.rr.secureprint.entity.UserEntity;
import com.rr.secureprint.repository.AuditLogRepository;
import com.rr.secureprint.service.AuthService;
import com.rr.secureprint.service.JobService;
import com.rr.secureprint.service.PrinterService;
import com.rr.secureprint.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final JobService jobService;
    private final AuthService authService;
    private final AuditLogRepository auditLogRepository;
    private final PrinterService printerService;
    private final UserService userService;

    public AdminController(JobService jobService,
                           AuthService authService,
                           AuditLogRepository auditLogRepository,
                           PrinterService printerService,
                           UserService userService) {
        this.jobService = jobService;
        this.authService = authService;
        this.auditLogRepository = auditLogRepository;
        this.printerService = printerService;
        this.userService = userService;
    }

    /* =========================================================================
       PRINTER MANAGEMENT ENDPOINTS
       ========================================================================= */

    @GetMapping("/printers")
    public ResponseEntity<List<PrinterEntity>> getPrinters(HttpServletRequest httpRequest) {
        return ResponseEntity.ok(printerService.getAllPrinters());
    }

    @GetMapping("/printers/default-hold")
    public ResponseEntity<?> getDefaultHoldPrinter() {
        return ResponseEntity.ok(printerService.getDefaultHoldPrinter().orElse(null));
    }

    @PostMapping("/printers/{id}/set-default-hold")
    public ResponseEntity<?> setDefaultHoldPrinter(@PathVariable("id") Long id, HttpServletRequest httpRequest) {
        if (!isAdmin(httpRequest)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Admin privileges required."));
        }
        try {
            String adminUser = getAdminActorName(httpRequest);
            PrinterEntity printer = printerService.setDefaultHoldPrinter(id, adminUser, httpRequest.getRemoteAddr());
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Printer '" + printer.getName() + "' (" + printer.getPrinterCode() + ") is now the Default Virtual Printer for Print Hold.",
                    "printer", printer
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/printers")
    public ResponseEntity<?> createPrinter(@RequestBody PrinterSaveRequest request, HttpServletRequest httpRequest) {
        if (!isAdmin(httpRequest)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Admin privileges required."));
        }
        try {
            String adminUser = getAdminActorName(httpRequest);
            PrinterEntity printer = printerService.createPrinter(request, adminUser, httpRequest.getRemoteAddr());
            return ResponseEntity.status(HttpStatus.CREATED).body(printer);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/printers/{id}")
    public ResponseEntity<?> updatePrinter(@PathVariable("id") Long id,
                                           @RequestBody PrinterSaveRequest request,
                                           HttpServletRequest httpRequest) {
        if (!isAdmin(httpRequest)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Admin privileges required."));
        }
        try {
            String adminUser = getAdminActorName(httpRequest);
            PrinterEntity printer = printerService.updatePrinter(id, request, adminUser, httpRequest.getRemoteAddr());
            return ResponseEntity.ok(printer);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/printers/{id}")
    public ResponseEntity<?> deletePrinter(@PathVariable("id") Long id,
                                           @RequestParam(value = "resetEwb", defaultValue = "true") boolean resetEwb,
                                           HttpServletRequest httpRequest) {
        if (!isAdmin(httpRequest)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Admin privileges required."));
        }
        try {
            String adminUser = getAdminActorName(httpRequest);
            printerService.deletePrinter(id, resetEwb, adminUser, httpRequest.getRemoteAddr());
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/printers/{id}/reset-ewb")
    public ResponseEntity<?> resetPrinterEwb(@PathVariable("id") Long id,
                                             @RequestParam(value = "reboot", defaultValue = "true") boolean reboot,
                                             HttpServletRequest httpRequest) {
        if (!isAdmin(httpRequest)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Admin privileges required."));
        }
        try {
            String adminUser = getAdminActorName(httpRequest);
            boolean success = printerService.resetPrinterEwb(id, reboot, adminUser, httpRequest.getRemoteAddr());
            return ResponseEntity.ok(Map.of(
                    "success", success,
                    "message", success
                            ? "Toshiba EWB cleared and Default Screen restored to Home Screen!" + (reboot ? " Device is rebooting..." : "")
                            : "Failed to reset Toshiba EWB. Check printer connection and admin credentials."
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/printers/{id}/reboot")
    public ResponseEntity<?> rebootPrinter(@PathVariable("id") Long id, HttpServletRequest httpRequest) {
        if (!isAdmin(httpRequest)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Admin privileges required."));
        }
        try {
            String adminUser = getAdminActorName(httpRequest);
            boolean success = printerService.rebootPrinter(id, adminUser, httpRequest.getRemoteAddr());
            return ResponseEntity.ok(Map.of("success", success, "message", success ? "Reboot signal accepted by Toshiba controller." : "Reboot failed. Check printer connection."));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/printers/{id}/test")
    public ResponseEntity<PrinterTestResult> testPrinterConnection(@PathVariable("id") Long id, HttpServletRequest httpRequest) {
        String adminUser = getAdminActorName(httpRequest);
        PrinterTestResult result = printerService.testPrinterConnection(id, adminUser, httpRequest.getRemoteAddr());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/printers/{id}/configure-ewb")
    public ResponseEntity<?> configurePrinterEwb(@PathVariable("id") Long id,
                                                 @RequestBody(required = false) EwbConfigRequest request,
                                                 HttpServletRequest httpRequest) {
        if (!isAdmin(httpRequest)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Admin privileges required."));
        }
        try {
            String adminUser = getAdminActorName(httpRequest);
            EwbConfigRequest req = request != null ? request : new EwbConfigRequest(null, null, null, null);
            EwbConfigResult result = printerService.configurePrinterEwb(id, req, adminUser, httpRequest.getRemoteAddr());
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/printers/{id}/ewb-status")
    public ResponseEntity<?> getPrinterEwbStatus(@PathVariable("id") Long id, HttpServletRequest httpRequest) {
        if (!isAdmin(httpRequest)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Admin privileges required."));
        }
        try {
            EwbConfigResult result = printerService.getPrinterEwbStatus(id);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/printers/test-ewb-direct")
    public ResponseEntity<?> testEwbDirect(@RequestBody EwbConfigRequest request, HttpServletRequest httpRequest) {
        if (!isAdmin(httpRequest)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Admin privileges required."));
        }
        try {
            EwbConfigResult result = printerService.testEwbDirect(request);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/printers/seed-samples")
    public ResponseEntity<?> seedSamplePrinters(
            @RequestParam(value = "count", defaultValue = "20") int count,
            HttpServletRequest httpRequest) {
        if (!isAdmin(httpRequest)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Admin privileges required."));
        }
        try {
            String adminUser = getAdminActorName(httpRequest);
            List<PrinterEntity> seeded = printerService.seedSamplePrinters(Math.min(count, 100), adminUser, httpRequest.getRemoteAddr());
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Successfully generated " + seeded.size() + " sample Toshiba MFPs for large fleet testing.",
                    "count", seeded.size()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    /* =========================================================================
       USER MANAGEMENT ENDPOINTS
       ========================================================================= */

    @GetMapping("/users")
    public ResponseEntity<?> getUsers(HttpServletRequest httpRequest) {
        if (!isAdmin(httpRequest)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Admin privileges required."));
        }
        return ResponseEntity.ok(userService.getAllUsers());
    }

    @PostMapping("/users")
    public ResponseEntity<?> createUser(@RequestBody UserSaveRequest request, HttpServletRequest httpRequest) {
        if (!isAdmin(httpRequest)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Admin privileges required."));
        }
        try {
            String adminUser = getAdminActorName(httpRequest);
            UserDto user = userService.createUser(request, adminUser, httpRequest.getRemoteAddr());
            return ResponseEntity.status(HttpStatus.CREATED).body(user);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/users/{id}")
    public ResponseEntity<?> updateUser(@PathVariable("id") Long id,
                                        @RequestBody UserSaveRequest request,
                                        HttpServletRequest httpRequest) {
        if (!isAdmin(httpRequest)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Admin privileges required."));
        }
        try {
            String adminUser = getAdminActorName(httpRequest);
            UserDto user = userService.updateUser(id, request, adminUser, httpRequest.getRemoteAddr());
            return ResponseEntity.ok(user);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<?> deleteUser(@PathVariable("id") Long id, HttpServletRequest httpRequest) {
        if (!isAdmin(httpRequest)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Admin privileges required."));
        }
        try {
            String adminUser = getAdminActorName(httpRequest);
            userService.deleteUser(id, adminUser, httpRequest.getRemoteAddr());
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/users/{id}/reset-pin")
    public ResponseEntity<?> resetPin(@PathVariable("id") Long id,
                                      @RequestBody Map<String, String> body,
                                      HttpServletRequest httpRequest) {
        if (!isAdmin(httpRequest)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Admin privileges required."));
        }
        String pin = body.get("pin");
        if (pin == null || pin.trim().length() < 4) {
            return ResponseEntity.badRequest().body(Map.of("error", "PIN must be at least 4 digits."));
        }
        String adminUser = getAdminActorName(httpRequest);
        userService.resetPin(id, pin, adminUser, httpRequest.getRemoteAddr());
        return ResponseEntity.ok(Map.of("message", "PIN successfully reset."));
    }

    /* =========================================================================
       PRINT JOB QUEUE & AUDIT ENDPOINTS
       ========================================================================= */

    @GetMapping("/jobs")
    public ResponseEntity<?> getAllJobs(HttpServletRequest httpRequest) {
        if (!isAdmin(httpRequest)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Admin privileges required."));
        }
        return ResponseEntity.ok(jobService.getAllJobsForAdmin());
    }

    @GetMapping("/audit")
    public ResponseEntity<?> getAuditLogs(HttpServletRequest httpRequest) {
        if (!isAdmin(httpRequest)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Admin privileges required."));
        }
        List<AuditLogDto> logs = auditLogRepository.findTop100ByOrderByTimestampDesc().stream()
                .map(AuditLogDto::fromEntity)
                .toList();
        return ResponseEntity.ok(logs);
    }

    @GetMapping("/stats")
    public ResponseEntity<?> getStats(HttpServletRequest httpRequest) {
        List<JobDto> jobs = jobService.getAllJobsForAdmin();
        long held = jobs.stream().filter(j -> j.status() == com.rr.secureprint.entity.JobStatus.HELD).count();
        long completed = jobs.stream().filter(j -> j.status() == com.rr.secureprint.entity.JobStatus.COMPLETED).count();
        long printersCount = printerService.getAllPrinters().size();
        long usersCount = userService.getAllUsers().size();

        return ResponseEntity.ok(Map.of(
                "totalJobs", jobs.size(),
                "heldJobs", held,
                "completedJobs", completed,
                "printerCount", printersCount,
                "userCount", usersCount
        ));
    }

    /* =========================================================================
       SECURITY CONTEXT HELPERS
       ========================================================================= */

    private boolean isAdmin(HttpServletRequest httpRequest) {
        Optional<UserEntity> userOpt = authService.getCurrentUser(httpRequest);
        if (userOpt.isPresent() && "ADMIN".equalsIgnoreCase(userOpt.get().getRole())) {
            return true;
        }
        String ip = httpRequest.getRemoteAddr();
        if (ip == null) return true;
        return ip.equals("127.0.0.1") ||
                ip.equals("0:0:0:0:0:0:0:1") ||
                ip.startsWith("192.168.") ||
                ip.startsWith("10.") ||
                ip.startsWith("172.") ||
                "localhost".equalsIgnoreCase(httpRequest.getServerName());
    }

    private String getAdminActorName(HttpServletRequest httpRequest) {
        return authService.getCurrentUser(httpRequest)
                .map(UserEntity::getUsername)
                .orElse("admin");
    }
}
