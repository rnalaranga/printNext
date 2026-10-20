package com.rr.secureprint.service;

import com.rr.secureprint.dto.EwbConfigRequest;
import com.rr.secureprint.dto.EwbConfigResult;
import com.rr.secureprint.dto.PrinterSaveRequest;
import com.rr.secureprint.dto.PrinterTestResult;
import com.rr.secureprint.entity.PrinterEntity;
import com.rr.secureprint.repository.PrinterRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class PrinterService {

    private static final Logger log = LoggerFactory.getLogger(PrinterService.class);

    private final PrinterRepository printerRepository;
    private final RawSocketPrinterService rawSocketPrinterService;
    private final ToshibaEwbService toshibaEwbService;
    private final com.rr.secureprint.repository.PrintJobRepository printJobRepository;
    private final AuditService auditService;

    public PrinterService(PrinterRepository printerRepository,
                          RawSocketPrinterService rawSocketPrinterService,
                          ToshibaEwbService toshibaEwbService,
                          com.rr.secureprint.repository.PrintJobRepository printJobRepository,
                          AuditService auditService) {
        this.printerRepository = printerRepository;
        this.rawSocketPrinterService = rawSocketPrinterService;
        this.toshibaEwbService = toshibaEwbService;
        this.printJobRepository = printJobRepository;
        this.auditService = auditService;
    }

    public List<PrinterEntity> getAllPrinters() {
        return printerRepository.findAll();
    }

    public Optional<PrinterEntity> getPrinterById(Long id) {
        return printerRepository.findById(id);
    }

    @Transactional
    public PrinterEntity createPrinter(PrinterSaveRequest request, String adminActor, String clientIp) {
        String code = request.printerCode().trim().toUpperCase();
        if (printerRepository.findByPrinterCode(code).isPresent()) {
            throw new IllegalArgumentException("Printer code '" + code + "' already registered.");
        }

        PrinterEntity printer = new PrinterEntity(
                code,
                request.name() != null ? request.name().trim() : code,
                request.ipAddress().trim(),
                request.rawPort() != null ? request.rawPort() : 9100,
                request.location() != null ? request.location().trim() : "Office",
                request.model() != null ? request.model().trim() : "Toshiba e-STUDIO 3518A",
                request.colorSupported() != null && request.colorSupported(),
                request.duplexSupported() == null || request.duplexSupported()
        );

        if (request.adminUsername() != null && !request.adminUsername().isBlank()) {
            printer.setAdminUsername(request.adminUsername().trim());
        }
        if (request.adminPassword() != null && !request.adminPassword().isBlank()) {
            printer.setAdminPassword(request.adminPassword().trim());
        }

        if (request.isActive() != null) {
            printer.setActive(request.isActive());
        }

        // Auto-configure EWB if requested
        if (Boolean.TRUE.equals(request.autoConfigureEwb())) {
            String termUrl = request.terminalUrl() != null && !request.terminalUrl().isBlank()
                    ? request.terminalUrl().trim()
                    : "http://" + (clientIp != null && !clientIp.contains(":") ? clientIp : "localhost") + ":8080/";

            EwbConfigResult ewbResult = toshibaEwbService.configureEwb(
                    printer.getIpAddress(),
                    printer.getAdminUsername(),
                    printer.getAdminPassword(),
                    termUrl
            );

            if (ewbResult.success()) {
                printer.setEwbConfigured(true);
                printer.setEwbUrl(ewbResult.configuredUrl());
                printer.setEwbConfiguredAt(Instant.now());
                if (ewbResult.printerModel() != null && !ewbResult.printerModel().isBlank()) {
                    printer.setModel(ewbResult.printerModel());
                }
            }
        }

        PrinterEntity saved = printerRepository.save(printer);

        auditService.logEvent("PRINTER_ADDED", null, adminActor, null, saved.getPrinterCode(), clientIp,
                "Registered new printer: " + saved.getName() + " (" + saved.getIpAddress() + ":" + saved.getRawPort() + ")" +
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

        // Auto-configure EWB if requested
        if (Boolean.TRUE.equals(request.autoConfigureEwb())) {
            String termUrl = request.terminalUrl() != null && !request.terminalUrl().isBlank()
                    ? request.terminalUrl().trim()
                    : (printer.getEwbUrl() != null ? printer.getEwbUrl() : "http://localhost:8080/");

            EwbConfigResult ewbResult = toshibaEwbService.configureEwb(
                    printer.getIpAddress(),
                    printer.getAdminUsername(),
                    printer.getAdminPassword(),
                    termUrl
            );

            if (ewbResult.success()) {
                printer.setEwbConfigured(true);
                printer.setEwbUrl(ewbResult.configuredUrl());
                printer.setEwbConfiguredAt(Instant.now());
                if (ewbResult.printerModel() != null && !ewbResult.printerModel().isBlank()) {
                    printer.setModel(ewbResult.printerModel());
                }
            }
        }

        PrinterEntity updated = printerRepository.save(printer);

        auditService.logEvent("PRINTER_UPDATED", null, adminActor, null, updated.getPrinterCode(), clientIp,
                "Updated printer configuration for " + updated.getName(), "SUCCESS");

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
        if (resetDeviceEwb && printer.getIpAddress() != null && !printer.getIpAddress().isBlank()) {
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

        printerRepository.delete(printer);

        auditService.logEvent("PRINTER_DELETED", null, adminActor, null, printer.getPrinterCode(), clientIp,
                "Deleted printer: " + printer.getName() + " (" + printer.getPrinterCode() + ")" +
                        (resetDeviceEwb ? " [EWB reset to Home Screen]" : ""), "WARNING");
    }

    @Transactional
    public boolean resetPrinterEwb(Long id, boolean reboot, String adminActor, String clientIp) {
        PrinterEntity printer = printerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Printer id " + id + " not found."));

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

        String targetUrl = request.terminalUrl() != null && !request.terminalUrl().isBlank()
                ? request.terminalUrl().trim()
                : (printer.getEwbUrl() != null ? printer.getEwbUrl() : "http://localhost:8080/");

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
            printerRepository.save(printer);
        }

        auditService.logEvent("PRINTER_EWB_CONFIGURED", null, adminActor, null, printer.getPrinterCode(), clientIp,
                "Toshiba EWB configuration: " + result.message(), result.success() ? "SUCCESS" : "FAILURE");

        return result;
    }

    public EwbConfigResult getPrinterEwbStatus(Long id) {
        PrinterEntity printer = printerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Printer id " + id + " not found."));
        return toshibaEwbService.getEwbStatus(printer.getIpAddress());
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
}
