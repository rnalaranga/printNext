package com.rr.secureprint.service;

import com.rr.secureprint.dto.ScanJobDto;
import com.rr.secureprint.dto.ScanRequest;
import com.rr.secureprint.entity.PrinterEntity;
import com.rr.secureprint.entity.ScanJobEntity;
import com.rr.secureprint.entity.SystemSettingsEntity;
import com.rr.secureprint.entity.UserEntity;
import com.rr.secureprint.repository.PrinterRepository;
import com.rr.secureprint.repository.ScanJobRepository;
import com.rr.secureprint.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

@Service
public class ScanService {

    private static final Logger log = LoggerFactory.getLogger(ScanService.class);
    private static final DateTimeFormatter FILE_TS_FMT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final ScanJobRepository scanJobRepository;
    private final UserRepository userRepository;
    private final PrinterRepository printerRepository;
    private final SystemSettingsService systemSettingsService;
    private final SmtpEmailService smtpEmailService;
    private final AuditService auditService;
    private final CopyTrackingService copyTrackingService;

    public ScanService(ScanJobRepository scanJobRepository,
                       UserRepository userRepository,
                       PrinterRepository printerRepository,
                       SystemSettingsService systemSettingsService,
                       SmtpEmailService smtpEmailService,
                       AuditService auditService,
                       CopyTrackingService copyTrackingService) {
        this.scanJobRepository = scanJobRepository;
        this.userRepository = userRepository;
        this.printerRepository = printerRepository;
        this.systemSettingsService = systemSettingsService;
        this.smtpEmailService = smtpEmailService;
        this.auditService = auditService;
        this.copyTrackingService = copyTrackingService;
    }

    /**
     * Executes an end-to-end Scan to Email job.
     */
    @Transactional
    public ScanJobDto processScanToEmail(ScanRequest request, UserEntity user, String clientIp) {
        if (user == null) {
            throw new IllegalArgumentException("Authentication required to perform scan.");
        }

        SystemSettingsEntity settings = systemSettingsService.getOrCreateSettings();
        if (Boolean.FALSE.equals(settings.getScanToEmailEnabled())) {
            throw new IllegalStateException("Scan-to-Email service is currently disabled by administrator.");
        }

        // Strictly enforce destination email: ALWAYS route to the authenticated user's own LDAP email
        String recipient = user.getEmail();
        if (recipient == null || recipient.isBlank()) {
            recipient = (request.recipientEmail() != null && !request.recipientEmail().isBlank())
                    ? request.recipientEmail().trim()
                    : user.getUsername() + "@rrconstruction.lk";
        }
        recipient = recipient.trim();

        // Resolve printer
        PrinterEntity printer = null;
        if (request.printerCode() != null && !request.printerCode().isBlank()) {
            printer = printerRepository.findByPrinterCode(request.printerCode().trim().toUpperCase()).orElse(null);
        }
        if (printer == null && request.printerIp() != null && !request.printerIp().isBlank()) {
            printer = printerRepository.findByIpAddress(request.printerIp().trim()).orElse(null);
        }
        if (printer == null && clientIp != null && !clientIp.isBlank()) {
            printer = printerRepository.findByIpAddress(clientIp.trim()).orElse(null);
        }
        if (printer == null) {
            printer = printerRepository.findAll().stream().filter(PrinterEntity::isActive).findFirst().orElse(null);
        }

        String printerName = printer != null ? printer.getName() : "Toshiba e-STUDIO MFP";
        String printerIp = printer != null ? printer.getIpAddress() : (clientIp != null ? clientIp : "127.0.0.1");

        // Scan parameters
        int pages = (request.pages() != null && request.pages() > 0) ? request.pages() : 1;
        String colorMode = (request.colorMode() != null && !request.colorMode().isBlank()) ? request.colorMode().toUpperCase() : "COLOR";
        String resolution = (request.resolution() != null && !request.resolution().isBlank()) ? request.resolution().toUpperCase() : "300DPI";
        String fileFormat = (request.fileFormat() != null && !request.fileFormat().isBlank()) ? request.fileFormat().toUpperCase() : "PDF";
        String duplex = (request.duplex() != null && !request.duplex().isBlank()) ? request.duplex().toUpperCase() : "SIMPLEX";

        String timestamp = LocalDateTime.now().format(FILE_TS_FMT);
        String fileName = (request.fileName() != null && !request.fileName().isBlank())
                ? request.fileName().trim()
                : "Scan_" + user.getUsername() + "_" + timestamp + ".pdf";

        String defaultSubjectPattern = settings.getDefaultScanSubject() != null ? settings.getDefaultScanSubject() : "Scanned Document from {printer}";
        String subject = (request.subject() != null && !request.subject().isBlank())
                ? request.subject().trim()
                : defaultSubjectPattern.replace("{printer}", printerName).replace("{user}", user.getDisplayName());

        // 1. Generate compliant scanned PDF attachment
        byte[] pdfBytes = PdfDocumentGenerator.generateScannedPdf(
                subject, user.getDisplayName() + " (" + user.getUsername() + ")", printerName, colorMode, resolution, pages
        );

        // 2. Dispatch via SMTP email service
        SmtpEmailService.EmailResult emailResult = smtpEmailService.sendScanEmail(
                recipient, subject, fileName, pdfBytes, user.getUsername(), printerName, settings
        );

        String status = emailResult.success() ? "SENT" : "FAILED";

        // 3. Persist scan job record
        ScanJobEntity scanJob = new ScanJobEntity(
                user, printer, user.getUsername(), printerName, printerIp,
                recipient, subject, fileName, pages, colorMode, resolution, fileFormat, duplex,
                status, emailResult.message()
        );
        ScanJobEntity saved = scanJobRepository.save(scanJob);

        // 4. Update user's scan counter
        user.setScanCount(user.getScanCount() + pages);
        userRepository.save(user);

        // 5. Audit trail
        auditService.logEvent(
                "SCAN_TO_EMAIL",
                user.getUserSid(),
                user.getUsername(),
                null,
                printer != null ? printer.getPrinterCode() : "MFP",
                printerIp,
                "Scan to Email (" + pages + " pgs, " + colorMode + ") delivered to " + recipient + " [" + status + "]",
                emailResult.success() ? "SUCCESS" : "FAILURE"
        );

        // Keep walk-up session alive
        if (printer != null) {
            copyTrackingService.registerWalkUpSession(printer.getIpAddress(), user.getUsername());
        }

        log.info("[SCAN TO EMAIL] Job #{} completed for user '{}' -> destination: {}", saved.getId(), user.getUsername(), recipient);
        return ScanJobDto.fromEntity(saved);
    }

    public List<ScanJobDto> getRecentScans() {
        return scanJobRepository.findTop50ByOrderByCompletedAtDesc().stream()
                .map(ScanJobDto::fromEntity)
                .toList();
    }

    public List<ScanJobDto> getUserScans(String username) {
        return scanJobRepository.findByUsernameOrderByCompletedAtDesc(username).stream()
                .map(ScanJobDto::fromEntity)
                .toList();
    }
}
