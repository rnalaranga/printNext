package com.rr.secureprint.service;

import com.rr.secureprint.dto.JobDto;
import com.rr.secureprint.dto.SimulationJobRequest;
import com.rr.secureprint.entity.JobStatus;
import com.rr.secureprint.entity.PrintJob;
import com.rr.secureprint.entity.PrinterEntity;
import com.rr.secureprint.entity.UserEntity;
import com.rr.secureprint.repository.PrintJobRepository;
import com.rr.secureprint.repository.PrinterRepository;
import com.rr.secureprint.repository.UserRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Service for managing simulated print queues, mock job generation,
 * and automated queue lifecycle expiration.
 */
@Service
public class SimulationService {

    private static final Logger LOGGER = Logger.getLogger(SimulationService.class.getName());

    private final PrintJobRepository printJobRepository;
    private final UserRepository userRepository;
    private final PrinterRepository printerRepository;
    private final SpoolStorageService spoolStorageService;
    private final AuditService auditService;

    public SimulationService(PrintJobRepository printJobRepository,
                             UserRepository userRepository,
                             PrinterRepository printerRepository,
                             SpoolStorageService spoolStorageService,
                             AuditService auditService) {
        this.printJobRepository = printJobRepository;
        this.userRepository = userRepository;
        this.printerRepository = printerRepository;
        this.spoolStorageService = spoolStorageService;
        this.auditService = auditService;
    }

    /**
     * Injects a simulated print job into the secure holding queue.
     * Generates simulated printer-ready PCL/PostScript payload, computes SHA-256,
     * and saves metadata to the database.
     */
    @Transactional
    public JobDto submitSimulatedJob(SimulationJobRequest request) throws IOException {
        String username = request.username().toLowerCase().trim();
        UserEntity user = userRepository.findByUsername(username)
                .orElseGet(() -> {
                    // Create simulated user if not yet present
                    UserEntity u = new UserEntity(username, username.toUpperCase(),
                            "S-1-5-21-3623811015-3361044348-30300820-" + (1000 + (username.hashCode() & 0xFF)),
                            null, null, username + "@rrconstruction.local", "General Engineering", "USER");
                    return userRepository.save(u);
                });

        // Generate simulated printer-ready spool data (PCL-6 / PJL envelope)
        byte[] spoolData = generateSimulatedSpoolBytes(user.getUsername(), request.documentName(),
                request.pageCount(), request.color(), request.duplex());

        // Store safely in spool_data/ directory with SHA-256 validation
        SpoolStorageService.StoredFileResult storedFile =
                spoolStorageService.storeSpoolData(spoolData, "simulated_" + user.getUsername());

        // PaperCut Find-Me Printing: Held jobs are not bound to any physical printer until release
        PrinterEntity targetPrinter = null;

        UUID jobId = UUID.randomUUID();
        Instant expiresAt = Instant.now().plus(24, ChronoUnit.HOURS);

        PrintJob job = new PrintJob(
                jobId,
                user,
                user.getUserSid(),
                user.getUsername(),
                request.documentName(),
                request.pageCount(),
                request.color(),
                request.duplex(),
                request.copies(),
                storedFile.sizeBytes(),
                storedFile.absolutePath(),
                storedFile.sha256(),
                request.clientMachine(),
                expiresAt
        );
        job.setTargetPrinter(targetPrinter);

        PrintJob savedJob = printJobRepository.save(job);

        auditService.logEvent("JOB_SUBMITTED", user.getUserSid(), user.getUsername(), jobId,
                targetPrinter != null ? targetPrinter.getPrinterCode() : "VIRTUAL-QUEUE",
                "127.0.0.1", "Job submitted to Virtual Follow-Me Queue: " + request.documentName(), "SUCCESS");

        LOGGER.info("Simulated job submitted successfully to Virtual Queue: " + jobId + " for " + user.getUsername());
        return JobDto.fromEntity(savedJob);
    }

    /**
     * Ingests a real print job intercepted from the Windows Print Spooler.
     * Auto-registers the user identity (SID & username) if not previously enrolled.
     */
    @Transactional
    public JobDto ingestWindowsSpoolJob(com.rr.secureprint.dto.SpoolIngestRequest request) throws IOException {
        String username = request.username().trim();
        String userSid = (request.userSid() != null && !request.userSid().isBlank())
                ? request.userSid()
                : "S-1-5-21-1966046810-3588821400-173210280-1001";

        UserEntity user = userRepository.findByUserSid(userSid)
                .or(() -> userRepository.findByUsername(username.toLowerCase()))
                .orElseGet(() -> {
                    UserEntity newUser = new UserEntity(
                            username.toLowerCase(),
                            username + " (Windows User)",
                            userSid,
                            "1234",
                            "BADGE-" + Math.abs(username.hashCode() % 10000),
                            username.toLowerCase() + "@rrconstruction.local",
                            "RR Construction",
                            "USER"
                    );
                    return userRepository.save(newUser);
                });

        byte[] spoolData;
        if (request.spoolBase64() != null && !request.spoolBase64().isBlank()) {
            spoolData = java.util.Base64.getDecoder().decode(request.spoolBase64().trim());
        } else if (request.spoolFilePath() != null && !request.spoolFilePath().isBlank() && new java.io.File(request.spoolFilePath()).exists()) {
            spoolData = java.nio.file.Files.readAllBytes(java.nio.file.Path.of(request.spoolFilePath()));
        } else {
            spoolData = generateSimulatedSpoolBytes(user.getUsername(), request.documentName(),
                    request.pageCount(), request.color(), request.duplex());
        }

        SpoolStorageService.StoredFileResult stored =
                spoolStorageService.storeSpoolData(spoolData, "win_spool_" + user.getUsername());

        UUID jobId = UUID.randomUUID();
        Instant expiresAt = Instant.now().plus(24, ChronoUnit.HOURS);
        long sizeBytes = request.sizeBytes() > 0 ? request.sizeBytes() : stored.sizeBytes();

        PrintJob job = new PrintJob(
                jobId,
                user,
                user.getUserSid(),
                user.getUsername(),
                request.documentName(),
                request.pageCount(),
                request.color(),
                request.duplex(),
                1,
                sizeBytes,
                stored.absolutePath(),
                stored.sha256(),
                request.clientMachine(),
                expiresAt
        );
        // In PaperCut Follow-Me Architecture: job is held in Virtual Queue without locking to a physical device
        job.setTargetPrinter(null);
        job.setWindowsJobId(request.windowsJobId());

        PrintJob saved = printJobRepository.save(job);

        auditService.logEvent("JOB_HELD", user.getUserSid(), user.getUsername(), jobId,
                "VIRTUAL-QUEUE",
                "127.0.0.1", "Real Windows print job held: " + request.documentName() + " (" + request.pageCount() + " pages)", "SUCCESS");

        LOGGER.info("[HOLD PRINT SUCCESS] Ingested Windows job " + jobId + " for user " + user.getUsername() + " (" + user.getUserSid() + ")");
        return JobDto.fromEntity(saved);
    }

    /**
     * Seeds initial demonstration print jobs if queue is currently empty.
     */
    @Transactional
    public void seedInitialDemoJobs() {
        // Remove fake demo users if present
        userRepository.findByUsername("jdoe").ifPresent(userRepository::delete);
        userRepository.findByUsername("msmith").ifPresent(userRepository::delete);

        // Ensure real user RR exists with PIN 1234
        userRepository.findByUsername("rr").ifPresentOrElse(
                u -> { u.setPinHash("1234"); userRepository.save(u); },
                () -> {
                    UserEntity rrUser = new UserEntity(
                            "rr",
                            "RR",
                            "S-1-5-21-1966046810-3588821400-173210280-1001",
                            "1234",
                            "BADGE-1001",
                            "rr@rrconstruction.local",
                            "Operations",
                            "USER"
                    );
                    userRepository.save(rrUser);
                }
        );

        userRepository.findByUsername("admin").ifPresent(u -> { u.setPinHash("9999"); userRepository.save(u); });

        // Purge any lingering dummy jobs
        printJobRepository.findAll().stream()
                .filter(job -> "jdoe".equalsIgnoreCase(job.getUsername()) || "msmith".equalsIgnoreCase(job.getUsername()) || "admin".equalsIgnoreCase(job.getUsername()))
                .forEach(printJobRepository::delete);

        // Ensure at least the primary physical Toshiba printer exists
        if (printerRepository.count() == 0) {
            PrinterEntity p = new PrinterEntity(
                    "TOSHIBA-3518A-HQ",
                    "Toshiba e-STUDIO 3518A",
                    "192.168.1.8",
                    9100,
                    "Main Office - Print Bay",
                    "Toshiba e-STUDIO 3518A",
                    false,
                    true
            );
            p.setDefaultHoldQueue(true);
            printerRepository.save(p);
        }

        LOGGER.info("Printer registry initialized with " + printerRepository.count() + " active devices.");
    }

    /**
     * Scheduled background job to expire old unreleased print jobs (every hour).
     */
    @Scheduled(fixedRate = 3600000)
    @Transactional
    public void cleanupExpiredJobs() {
        Instant now = Instant.now();
        List<PrintJob> expiredJobs = printJobRepository.findByStatusAndExpiresAtBefore(JobStatus.HELD, now);
        for (PrintJob job : expiredJobs) {
            LOGGER.info("Expiring stale job: " + job.getId() + " (" + job.getDocumentName() + ")");
            job.setStatus(JobStatus.EXPIRED);
            spoolStorageService.deleteSpoolFile(job.getSpoolFilePath());
            printJobRepository.save(job);

            auditService.logEvent("JOB_EXPIRED", job.getUserSid(), job.getUsername(), job.getId(),
                    null, "SYSTEM", "Job retention expired without release; spool purged", "WARNING");
        }
    }

    private PrinterEntity getDefaultHoldPrinter() {
        return printerRepository.findAll().stream()
                .filter(PrinterEntity::isDefaultHoldQueue)
                .findFirst()
                .or(() -> printerRepository.findByIsActiveTrue().stream().findFirst())
                .orElse(null);
    }

    private byte[] generateSimulatedSpoolBytes(String user, String docName, int pages, boolean color, boolean duplex) {
        StringBuilder sb = new StringBuilder();
        char esc = 0x1B;
        char ff = 0x0C;
        sb.append(esc).append("%-12345X@PJL\r\n");
        sb.append("@PJL SET JOBNAME = \"").append(docName).append("\"\r\n");
        sb.append("@PJL SET USERNAME = \"").append(user).append("\"\r\n");
        sb.append("@PJL SET DUPLEX = ").append(duplex ? "ON" : "OFF").append("\r\n");
        sb.append("@PJL ENTER LANGUAGE = PCL\r\n");
        sb.append(esc).append("E"); // PCL Reset
        sb.append(esc).append("&l0O"); // Portrait
        sb.append(esc).append("(s0p12h0s0b4099T\r\n\r\n"); // Standard Courier font
        sb.append("=================================================================\r\n");
        sb.append("   RR SECURE PRINT — TOSHIBA e-STUDIO 3518A RELEASED DOCUMENT    \r\n");
        sb.append("=================================================================\r\n");
        sb.append("Document: ").append(docName).append("\r\n");
        sb.append("User:     ").append(user).append("\r\n");
        sb.append("Pages:    ").append(pages).append("\r\n");
        sb.append("Printer:  Toshiba e-STUDIO 3518A (192.168.1.8)\r\n");
        sb.append("Status:   AUTHENTICATED PHYSICAL RELEASE SUCCESSFUL\r\n\r\n");
        for (int p = 1; p <= pages; p++) {
            sb.append("--- PAGE ").append(p).append(" OF ").append(pages).append(" ---\r\n\r\n");
            if (p < pages) {
                sb.append(ff); // Form feed between pages
            }
        }
        sb.append(ff); // Form feed to eject final sheet
        sb.append(esc).append("E"); // PCL Reset
        sb.append(esc).append("%-12345X");
        return sb.toString().getBytes(StandardCharsets.US_ASCII);
    }
}
