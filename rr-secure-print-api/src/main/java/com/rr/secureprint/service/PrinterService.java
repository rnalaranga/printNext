package com.rr.secureprint.service;

import com.rr.secureprint.dto.EwbConfigRequest;
import com.rr.secureprint.dto.EwbConfigResult;
import com.rr.secureprint.dto.PrinterSaveRequest;
import com.rr.secureprint.dto.PrinterTestResult;
import com.rr.secureprint.entity.PrinterEntity;
import com.rr.secureprint.repository.PrinterRepository;
import java.util.LinkedHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class PrinterService {

    private static final Logger log = LoggerFactory.getLogger(PrinterService.class);

    private final PrinterRepository printerRepository;
    private final RawSocketPrinterService rawSocketPrinterService;
    private final ToshibaEwbService toshibaEwbService;
    private final com.rr.secureprint.repository.PrintJobRepository printJobRepository;
    private final AuditService auditService;
    private final com.rr.secureprint.repository.DirectoryConfigRepository directoryConfigRepository;

    public PrinterService(PrinterRepository printerRepository,
                          RawSocketPrinterService rawSocketPrinterService,
                          ToshibaEwbService toshibaEwbService,
                          com.rr.secureprint.repository.PrintJobRepository printJobRepository,
                          AuditService auditService,
                          com.rr.secureprint.repository.DirectoryConfigRepository directoryConfigRepository) {
        this.printerRepository = printerRepository;
        this.rawSocketPrinterService = rawSocketPrinterService;
        this.toshibaEwbService = toshibaEwbService;
        this.printJobRepository = printJobRepository;
        this.auditService = auditService;
        this.directoryConfigRepository = directoryConfigRepository;
    }

    public List<PrinterEntity> getAllPrinters() {
        return printerRepository.findAll();
    }

    public Optional<PrinterEntity> getPrinterById(Long id) {
        return printerRepository.findById(id);
    }

    public Optional<PrinterEntity> getDefaultHoldPrinter() {
        return printerRepository.findAll().stream()
                .filter(PrinterEntity::isDefaultHoldQueue)
                .findFirst()
                .or(() -> printerRepository.findByIsActiveTrue().stream().findFirst());
    }

    @Transactional
    public PrinterEntity setDefaultHoldPrinter(Long id, String adminActor, String clientIp) {
        PrinterEntity target = printerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Printer id " + id + " not found."));

        // Demote all other printers
        List<PrinterEntity> all = printerRepository.findAll();
        for (PrinterEntity p : all) {
            if (p.isDefaultHoldQueue() && !p.getId().equals(id)) {
                p.setDefaultHoldQueue(false);
                printerRepository.save(p);
            }
        }

        target.setDefaultHoldQueue(true);
        target.setActive(true);
        PrinterEntity saved = printerRepository.save(target);

        auditService.logEvent("PRINTER_DEFAULT_HOLD_SET", null, adminActor, null,
                saved.getPrinterCode(), clientIp,
                "Designated printer " + saved.getName() + " (" + saved.getPrinterCode() + ", " + saved.getIpAddress() + ") as Default Virtual Printer for Print Hold.",
                "SUCCESS");

        log.info("Default virtual printer for print hold set to: {} [{}] ({})", saved.getName(), saved.getPrinterCode(), saved.getIpAddress());
        return saved;
    }

    @Transactional
    public PrinterEntity createPrinter(PrinterSaveRequest request, String adminActor, String clientIp) {
        String code = request.printerCode().trim().toUpperCase();
        if (printerRepository.findByPrinterCode(code).isPresent()) {
            throw new IllegalArgumentException("Printer code '" + code + "' already registered.");
        }

        String gen = request.deviceGeneration();
        if (gen == null || gen.isBlank()) {
            String m = request.model() != null ? request.model().toLowerCase() : "";
            if (m.contains("306") || m.contains("256") || m.contains("356") || m.contains("456") || m.contains("506") || m.contains("305") || m.contains("255")) {
                gen = "TOSHIBA_OLD";
            } else {
                gen = "TOSHIBA_NEW";
            }
        } else {
            gen = gen.trim().toUpperCase();
        }

        PrinterEntity printer = new PrinterEntity(
                code,
                request.name() != null ? request.name().trim() : code,
                request.ipAddress().trim(),
                request.rawPort() != null ? request.rawPort() : 9100,
                request.location() != null ? request.location().trim() : "Office",
                request.model() != null ? request.model().trim() : ("TOSHIBA_OLD".equalsIgnoreCase(gen) ? "Toshiba e-STUDIO 306SE" : "Toshiba e-STUDIO 3518A"),
                request.colorSupported() != null && request.colorSupported(),
                request.duplexSupported() == null || request.duplexSupported(),
                gen
        );

        if (request.adminUsername() != null && !request.adminUsername().isBlank()) {
            printer.setAdminUsername(request.adminUsername().trim());
        }
        if (request.adminPassword() != null && !request.adminPassword().isBlank()) {
            printer.setAdminPassword(request.adminPassword().trim());
        }
        if (request.department() != null && !request.department().isBlank()) {
            printer.setDepartment(request.department().trim());
        }

        if (request.isActive() != null) {
            printer.setActive(request.isActive());
        }

        // Manage Default Hold Queue flag
        boolean makeDefault = Boolean.TRUE.equals(request.isDefaultHoldQueue()) || printerRepository.count() == 0;
        if (makeDefault) {
            printerRepository.findAll().forEach(p -> {
                if (p.isDefaultHoldQueue()) {
                    p.setDefaultHoldQueue(false);
                    printerRepository.save(p);
                }
            });
            printer.setDefaultHoldQueue(true);
        }

        // Auto-configure EWB if requested (Only applicable to modern e-BRIDGE Next)
        if (Boolean.TRUE.equals(request.autoConfigureEwb())) {
            if ("TOSHIBA_OLD".equalsIgnoreCase(printer.getDeviceGeneration())) {
                log.info("[EWB AUTO-CONFIG] Skipped EWB push for legacy device {} ({}). Toshiba Old models release via Mobile/Web Station or RAW socket.", printer.getName(), printer.getModel());
            } else {
                try {
                    String termUrl = request.terminalUrl() != null && !request.terminalUrl().isBlank()
                            ? request.terminalUrl().trim()
                            : "http://" + (clientIp != null && !clientIp.contains(":") ? clientIp : "192.168.1.100") + ":8080/";

                    EwbConfigResult ewbResult = toshibaEwbService.configureEwb(
                            printer.getIpAddress(),
                            printer.getAdminUsername(),
                            printer.getAdminPassword(),
                            termUrl
                    );

                    if (ewbResult != null && ewbResult.success()) {
                        printer.setEwbConfigured(true);
                        printer.setEwbUrl(ewbResult.configuredUrl());
                        printer.setEwbConfiguredAt(Instant.now());
                        if (ewbResult.printerModel() != null && !ewbResult.printerModel().isBlank()) {
                            printer.setModel(ewbResult.printerModel());
                        }
                    }
                } catch (Exception e) {
                    log.warn("[EWB AUTO-CONFIG] EWB setup could not complete: " + e.getMessage());
                }
            }
        }

        PrinterEntity saved = printerRepository.save(printer);

        auditService.logEvent("PRINTER_ADDED", null, adminActor, null, saved.getPrinterCode(), clientIp,
                "Registered new printer: " + saved.getName() + " [" + saved.getDeviceGeneration() + "] (" + saved.getIpAddress() + ":" + saved.getRawPort() + ")" +
                        (saved.isDefaultHoldQueue() ? " [Default Hold Queue]" : "") +
                        (saved.isEwbConfigured() ? " with EWB auto-configured to " + saved.getEwbUrl() : ""), "SUCCESS");

        return saved;
    }

    @Transactional
    public PrinterEntity updatePrinter(Long id, PrinterSaveRequest request, String adminActor, String clientIp) {
        PrinterEntity printer = printerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Printer id " + id + " not found."));

        if (request.name() != null && !request.name().isBlank()) {
            printer.setName(request.name().trim());
        }
        if (request.deviceGeneration() != null && !request.deviceGeneration().isBlank()) {
            printer.setDeviceGeneration(request.deviceGeneration().trim());
        }
        if (request.ipAddress() != null && !request.ipAddress().isBlank()) {
            printer.setIpAddress(request.ipAddress().trim());
        }
        if (request.rawPort() != null) {
            printer.setRawPort(request.rawPort());
        }
        if (request.location() != null) {
            printer.setLocation(request.location().trim());
        }
        if (request.model() != null) {
            printer.setModel(request.model().trim());
        }
        if (request.colorSupported() != null) {
            printer.setColorSupported(request.colorSupported());
        }
        if (request.duplexSupported() != null) {
            printer.setDuplexSupported(request.duplexSupported());
        }
        if (request.isActive() != null) {
            printer.setActive(request.isActive());
        }
        if (request.adminUsername() != null && !request.adminUsername().isBlank()) {
            printer.setAdminUsername(request.adminUsername().trim());
        }
        if (request.adminPassword() != null && !request.adminPassword().isBlank()) {
            printer.setAdminPassword(request.adminPassword().trim());
        }
        if (request.department() != null && !request.department().isBlank()) {
            printer.setDepartment(request.department().trim());
        }
        if (request.department() != null && !request.department().isBlank()) {
            printer.setDepartment(request.department().trim());
        }

        if (Boolean.TRUE.equals(request.isDefaultHoldQueue())) {
            printerRepository.findAll().forEach(p -> {
                if (p.isDefaultHoldQueue() && !p.getId().equals(id)) {
                    p.setDefaultHoldQueue(false);
                    printerRepository.save(p);
                }
            });
            printer.setDefaultHoldQueue(true);
        } else if (request.isDefaultHoldQueue() != null && !request.isDefaultHoldQueue()) {
            printer.setDefaultHoldQueue(false);
        }

        // Auto-configure EWB if requested (Only applicable to modern e-BRIDGE Next)
        if (Boolean.TRUE.equals(request.autoConfigureEwb())) {
            if ("TOSHIBA_OLD".equalsIgnoreCase(printer.getDeviceGeneration())) {
                log.info("[EWB AUTO-CONFIG] Skipped EWB push for legacy device {} ({}).", printer.getName(), printer.getModel());
            } else {
                try {
                    String termUrl = request.terminalUrl() != null && !request.terminalUrl().isBlank()
                            ? request.terminalUrl().trim()
                            : (printer.getEwbUrl() != null ? printer.getEwbUrl() : "http://192.168.1.100:8080/");

                    EwbConfigResult ewbResult = toshibaEwbService.configureEwb(
                            printer.getIpAddress(),
                            printer.getAdminUsername(),
                            printer.getAdminPassword(),
                            termUrl
                    );

                    if (ewbResult != null && ewbResult.success()) {
                        printer.setEwbConfigured(true);
                        printer.setEwbUrl(ewbResult.configuredUrl());
                        printer.setEwbConfiguredAt(Instant.now());
                        if (ewbResult.printerModel() != null && !ewbResult.printerModel().isBlank()) {
                            printer.setModel(ewbResult.printerModel());
                        }
                    }
                } catch (Exception e) {
                    log.warn("[EWB AUTO-CONFIG] EWB update setup could not complete: " + e.getMessage());
                }
            }
        }

        PrinterEntity updated = printerRepository.save(printer);

        auditService.logEvent("PRINTER_UPDATED", null, adminActor, null, updated.getPrinterCode(), clientIp,
                "Updated printer configuration for " + updated.getName() + " [" + updated.getDeviceGeneration() + "]" +
                        (updated.isDefaultHoldQueue() ? " [Default Hold Queue]" : ""), "SUCCESS");

        return updated;
    }

    @Transactional
    public void deletePrinter(Long id, String adminActor, String clientIp) {
        deletePrinter(id, true, adminActor, clientIp);
    }

    @Transactional
    public void deletePrinter(Long id, boolean resetDeviceEwb, String adminActor, String clientIp) {
        PrinterEntity printer = printerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Printer id " + id + " not found."));

        // If requested, reset Toshiba EWB & DefaultScreen back to Home screen and restart device
        if (resetDeviceEwb && printer.getIpAddress() != null && !printer.getIpAddress().isBlank() && !"TOSHIBA_OLD".equalsIgnoreCase(printer.getDeviceGeneration())) {
            try {
                log.info("Resetting Toshiba EWB and restoring DefaultScreen to Home for printer {} ({}) prior to deletion...",
                        printer.getName(), printer.getIpAddress());
                toshibaEwbService.resetEwbAndRestoreDefaultScreen(
                        printer.getIpAddress(),
                        printer.getAdminUsername(),
                        printer.getAdminPassword(),
                        true
                );
            } catch (Exception ex) {
                log.warn("Non-critical: Could not reset Toshiba EWB during deletion of {}: {}",
                        printer.getIpAddress(), ex.getMessage());
            }
        }

        // Detach existing print jobs to avoid foreign key constraint violation
        printJobRepository.detachPrinterFromJobs(printer.getId());

        boolean wasDefault = printer.isDefaultHoldQueue();
        printerRepository.delete(printer);

        if (wasDefault) {
            printerRepository.findByIsActiveTrue().stream().findFirst().ifPresent(next -> {
                next.setDefaultHoldQueue(true);
                printerRepository.save(next);
                log.info("Auto-promoted printer {} as new default hold queue.", next.getName());
            });
        }

        auditService.logEvent("PRINTER_DELETED", null, adminActor, null, printer.getPrinterCode(), clientIp,
                "Deleted printer: " + printer.getName() + " (" + printer.getPrinterCode() + ")" +
                        (resetDeviceEwb ? " [EWB reset to Home Screen]" : ""), "WARNING");
    }

    @Transactional
    public boolean resetPrinterEwb(Long id, boolean reboot, String adminActor, String clientIp) {
        PrinterEntity printer = printerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Printer id " + id + " not found."));

        if ("TOSHIBA_OLD".equalsIgnoreCase(printer.getDeviceGeneration())) {
            log.info("Reset EWB called on Toshiba Old device {}. Skipped as legacy device does not use e-BRIDGE Next EWB.", printer.getName());
            return true;
        }

        boolean success = toshibaEwbService.resetEwbAndRestoreDefaultScreen(
                printer.getIpAddress(),
                printer.getAdminUsername(),
                printer.getAdminPassword(),
                reboot
        );

        if (success) {
            printer.setEwbConfigured(false);
            printer.setEwbUrl(null);
            printer.setEwbConfiguredAt(null);
            printerRepository.save(printer);
        }

        auditService.logEvent("PRINTER_EWB_RESET", null, adminActor, null, printer.getPrinterCode(), clientIp,
                "Reset Toshiba EWB and restored DefaultScreen to Home: " + (success ? "Success" : "Failed"), success ? "SUCCESS" : "FAILURE");

        return success;
    }

    @Transactional
    public boolean rebootPrinter(Long id, String adminActor, String clientIp) {
        PrinterEntity printer = printerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Printer id " + id + " not found."));

        boolean success = toshibaEwbService.rebootPrinter(
                printer.getIpAddress(),
                printer.getAdminUsername(),
                printer.getAdminPassword()
        );

        auditService.logEvent("PRINTER_REBOOT_TRIGGERED", null, adminActor, null, printer.getPrinterCode(), clientIp,
                "Reboot command sent to Toshiba controller: " + (success ? "Accepted" : "Failed"), success ? "SUCCESS" : "FAILURE");

        return success;
    }

    @Transactional
    public PrinterTestResult testPrinterConnection(Long id, String adminActor, String clientIp) {
        PrinterEntity printer = printerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Printer id " + id + " not found."));

        PrinterTestResult result = rawSocketPrinterService.testConnection(printer.getIpAddress(), printer.getRawPort());

        // Update status according to test result
        printer.setStatus(result.success() ? "ONLINE" : "OFFLINE");
        printerRepository.save(printer);

        auditService.logEvent("PRINTER_CONNECTION_TEST", null, adminActor, null, printer.getPrinterCode(), clientIp,
                "Connection probe: " + result.message(), result.success() ? "SUCCESS" : "FAILURE");

        return result;
    }

    @Transactional
    public EwbConfigResult configurePrinterEwb(Long id, EwbConfigRequest request, String adminActor, String clientIp) {
        PrinterEntity printer = printerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Printer id " + id + " not found."));

        String user = request.adminUsername() != null && !request.adminUsername().isBlank()
                ? request.adminUsername().trim()
                : (printer.getAdminUsername() != null ? printer.getAdminUsername() : "admin");

        String pass = request.adminPassword() != null && !request.adminPassword().isBlank()
                ? request.adminPassword().trim()
                : (printer.getAdminPassword() != null ? printer.getAdminPassword() : "123456");

        String serverHost = clientIp != null && !clientIp.contains(":") && !clientIp.equals("127.0.0.1") ? clientIp : "192.168.1.100";
        String targetUrl = request.terminalUrl() != null && !request.terminalUrl().isBlank()
                ? request.terminalUrl().trim()
                : (printer.getEwbUrl() != null && !printer.getEwbUrl().isBlank()
                    ? printer.getEwbUrl()
                    : "http://" + serverHost + ":8080/?printer=" + printer.getPrinterCode());

        if (!targetUrl.contains("printer=")) {
            targetUrl += (targetUrl.contains("?") ? "&" : "?") + "printer=" + printer.getPrinterCode();
        }

        EwbConfigResult result = toshibaEwbService.configureEwb(printer.getIpAddress(), user, pass, targetUrl);

        if (result.success()) {
            printer.setAdminUsername(user);
            printer.setAdminPassword(pass);
            printer.setEwbConfigured(true);
            printer.setEwbUrl(result.configuredUrl());
            printer.setEwbConfiguredAt(Instant.now());
            if (result.printerModel() != null && !result.printerModel().isBlank()) {
                printer.setModel(result.printerModel());
            }

            // Lock Toshiba HOME screen functions (Photocopy restriction) if requested or by default
            if (request.lockHomeFunctions() == null || Boolean.TRUE.equals(request.lockHomeFunctions())) {
                try {
                    boolean locked = toshibaEwbService.setCopyRestriction(printer.getIpAddress(), user, pass, true);
                    printer.setFunctionsLocked(locked);
                    log.info("Auto-locked Toshiba HOME screen functions for {}: {}", printer.getPrinterCode(), locked);
                } catch (Exception ex) {
                    log.warn("Could not auto-lock HOME functions during EWB push for {}: {}", printer.getPrinterCode(), ex.getMessage());
                }
            }

            printerRepository.save(printer);
        }

        auditService.logEvent("PRINTER_EWB_CONFIGURED", null, adminActor, null, printer.getPrinterCode(), clientIp,
                "Toshiba EWB configuration: " + result.message(), result.success() ? "SUCCESS" : "FAILURE");

        return result;
    }

    @Transactional
    public boolean lockPrinterFunctions(Long id, boolean lock, String adminActor, String clientIp) {
        PrinterEntity printer = printerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Printer id " + id + " not found."));

        if ("TOSHIBA_OLD".equalsIgnoreCase(printer.getDeviceGeneration())) {
            log.info("Lock functions called on legacy device {}. Not applicable.", printer.getName());
            return true;
        }

        String user = printer.getAdminUsername() != null && !printer.getAdminUsername().isBlank()
                ? printer.getAdminUsername() : "admin";
        String pass = printer.getAdminPassword() != null && !printer.getAdminPassword().isBlank()
                ? printer.getAdminPassword() : "123456";

        boolean success = toshibaEwbService.setCopyRestriction(printer.getIpAddress(), user, pass, lock);
        if (success) {
            printer.setFunctionsLocked(lock);
            printerRepository.save(printer);
        }

        auditService.logEvent(lock ? "PRINTER_FUNCTIONS_LOCKED" : "PRINTER_FUNCTIONS_UNLOCKED",
                null, adminActor, null, printer.getPrinterCode(), clientIp,
                (lock ? "Locked" : "Unlocked") + " Toshiba HOME screen functions (Photocopy restriction): " + (success ? "Success" : "Failed"),
                success ? "SUCCESS" : "FAILURE");

        return success;
    }

    @Transactional
    public Map<String, Object> lockAllPrintersFunctions(boolean lock, String adminActor, String clientIp) {
        List<PrinterEntity> printers = printerRepository.findAll();
        int total = 0;
        int succeeded = 0;
        int failed = 0;
        List<String> failedPrinters = new ArrayList<>();

        for (PrinterEntity p : printers) {
            if ("TOSHIBA_OLD".equalsIgnoreCase(p.getDeviceGeneration()) || !p.isActive()) {
                continue;
            }
            total++;
            boolean ok = lockPrinterFunctions(p.getId(), lock, adminActor, clientIp);
            if (ok) {
                succeeded++;
            } else {
                failed++;
                failedPrinters.add(p.getName() + " (" + p.getIpAddress() + ")");
            }
        }

        return Map.of(
                "total", total,
                "succeeded", succeeded,
                "failed", failed,
                "failedPrinters", failedPrinters,
                "message", "Successfully " + (lock ? "locked" : "unlocked") + " HOME screen icons on " + succeeded + " of " + total + " printers."
        );
    }

    public EwbConfigResult getPrinterEwbStatus(Long id) {
        PrinterEntity printer = printerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Printer id " + id + " not found."));
        return toshibaEwbService.getEwbStatus(printer.getIpAddress(), printer.getAdminUsername(), printer.getAdminPassword());
    }

    public Map<String, String> dumpPrinterSettings(Long id) {
        PrinterEntity printer = printerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Printer id " + id + " not found."));
        return toshibaEwbService.dumpAllSettings(printer.getIpAddress(), printer.getAdminUsername(), printer.getAdminPassword());
    }

        public EwbConfigResult pushLdapAndLockPrinter(Long id, String adminActor, String clientIp) {
        PrinterEntity printer = printerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Printer id " + id + " not found."));
        var dirConfig = directoryConfigRepository.findById(1L).orElse(null);
        String targetUrl = printer.getEwbUrl();
        EwbConfigResult result = toshibaEwbService.pushLdapAndLock(
                printer.getIpAddress(),
                printer.getAdminUsername(),
                printer.getAdminPassword(),
                targetUrl,
                dirConfig
        );
        if (result.success()) {
            printer.setFunctionsLocked(true);
            printer.setStatus("ONLINE");
            printerRepository.save(printer);
        }
        auditService.logEvent("PRINTER_PUSH_LDAP_LOCK", null, adminActor, null, printer.getPrinterCode(), clientIp,
                "Push LDAP & Function Lock on " + printer.getIpAddress() + ": " + result.message(), result.success() ? "SUCCESS" : "FAILURE");
        return result;
    }

    public EwbConfigResult pushLdapAndLockDirect(EwbConfigRequest request, String adminActor, String clientIp) {
        if (request.printerIp() == null || request.printerIp().isBlank()) {
            throw new IllegalArgumentException("Printer IP is required.");
        }
        var dirConfig = directoryConfigRepository.findById(1L).orElse(null);
        EwbConfigResult result = toshibaEwbService.pushLdapAndLock(
                request.printerIp(),
                request.adminUsername(),
                request.adminPassword(),
                request.terminalUrl(),
                dirConfig
        );
        auditService.logEvent("PRINTER_PUSH_LDAP_LOCK_DIRECT", null, adminActor, null, null, clientIp,
                "Direct Push LDAP & Function Lock on " + request.printerIp() + ": " + result.message(), result.success() ? "SUCCESS" : "FAILURE");
        return result;
    }

    public Map<String, Object> pushLdapAndLockAllFleet(String adminActor, String clientIp) {
        List<PrinterEntity> printers = printerRepository.findAll().stream()
                .filter(PrinterEntity::isActive)
                .filter(p -> !"TOSHIBA_OLD".equalsIgnoreCase(p.getDeviceGeneration()))
                .toList();

        var dirConfig = directoryConfigRepository.findById(1L).orElse(null);
        int successCount = 0;
        int failCount = 0;
        List<String> failedIps = new ArrayList<>();

        for (PrinterEntity p : printers) {
            try {
                EwbConfigResult r = toshibaEwbService.pushLdapAndLock(
                        p.getIpAddress(),
                        p.getAdminUsername(),
                        p.getAdminPassword(),
                        p.getEwbUrl(),
                        dirConfig
                );
                if (r.success()) {
                    p.setFunctionsLocked(true);
                    p.setStatus("ONLINE");
                    printerRepository.save(p);
                    successCount++;
                } else {
                    failCount++;
                    failedIps.add(p.getIpAddress());
                }
            } catch (Exception ex) {
                failCount++;
                failedIps.add(p.getIpAddress());
            }
        }

        auditService.logEvent("FLEET_PUSH_LDAP_LOCK", null, adminActor, null, null, clientIp,
                "Fleet LDAP & Card Lock applied: " + successCount + " success, " + failCount + " failed", "SUCCESS");

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("success", true);
        resp.put("totalCount", printers.size());
        resp.put("successCount", successCount);
        resp.put("failCount", failCount);
        resp.put("failedIps", failedIps);
        resp.put("message", "Fleet Push completed: " + successCount + " of " + printers.size() + " MFPs successfully updated with LDAP & Card Lockdown.");
        return resp;
    }

    public EwbConfigResult testEwbDirect(EwbConfigRequest request) {
        if (request.printerIp() == null || request.printerIp().isBlank()) {
            throw new IllegalArgumentException("Printer IP is required.");
        }
        return toshibaEwbService.configureEwb(
                request.printerIp(),
                request.adminUsername(),
                request.adminPassword(),
                request.terminalUrl()
        );
    }

    @Transactional
    public List<PrinterEntity> seedSamplePrinters(int count, String adminActor, String clientIp) {
        String[] locations = {
                "HQ - 1st Floor Reception", "HQ - 2nd Floor Finance", "HQ - 3rd Floor Engineering",
                "HQ - 4th Floor Executive", "Building B - Operations", "Warehouse - Shipping",
                "Building C - Legal", "Design Studio - Marketing"
        };
        String[] models = {
                "Toshiba e-STUDIO 2518A", "Toshiba e-STUDIO 3518A", "Toshiba e-STUDIO 4508A",
                "Toshiba e-STUDIO 5018A", "Toshiba e-STUDIO 2010AC (Color)", "Toshiba e-STUDIO 3015AC (Color)",
                "Toshiba e-STUDIO 306SE", "Toshiba e-STUDIO 256"
        };

        List<PrinterEntity> created = new ArrayList<>();
        long existingCount = printerRepository.count();

        for (int i = 1; i <= count; i++) {
            long seq = existingCount + i;
            String code = String.format("MFP-%04d", seq);
            if (printerRepository.findByPrinterCode(code).isPresent()) {
                code = String.format("MFP-%04d-%s", seq, UUID.randomUUID().toString().substring(0, 3).toUpperCase());
            }
            String loc = locations[(int) (seq % locations.length)];
            String model = models[(int) (seq % models.length)];
            boolean isColor = model.contains("(Color)");
            String gen = (model.contains("306") || model.contains("256")) ? "TOSHIBA_OLD" : "TOSHIBA_NEW";
            String ip = String.format("192.168.1.%d", 20 + (int)(seq % 200));
            String name = model.replace("Toshiba ", "") + " (" + loc.split(" - ")[1] + ")";

            PrinterEntity printer = new PrinterEntity(code, name, ip, 9100, loc, model, isColor, true, gen);
            created.add(printerRepository.save(printer));
        }

        auditService.logEvent("PRINTERS_BATCH_SEEDED", null, adminActor, null, "FLEET", clientIp,
                "Seeded " + created.size() + " sample Toshiba MFPs for large fleet testing.", "SUCCESS");

        return created;
    }
}
