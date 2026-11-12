package com.rr.secureprint.service;

import com.rr.secureprint.dto.JobDto;
import com.rr.secureprint.dto.ReleaseResponse;
import com.rr.secureprint.entity.JobStatus;
import com.rr.secureprint.entity.PrintJob;
import com.rr.secureprint.entity.PrinterEntity;
import com.rr.secureprint.entity.UserEntity;
import com.rr.secureprint.exception.JobConcurrencyException;
import com.rr.secureprint.exception.JobNotFoundException;
import com.rr.secureprint.repository.PrintJobRepository;
import com.rr.secureprint.repository.PrinterRepository;
import com.rr.secureprint.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

@Service
public class JobService {

    private static final Logger LOGGER = Logger.getLogger(JobService.class.getName());

    private final PrintJobRepository printJobRepository;
    private final PrinterRepository printerRepository;
    private final UserRepository userRepository;
    private final SpoolStorageService spoolStorageService;
    private final RawSocketPrinterService rawSocketPrinterService;
    private final WindowsQueueService windowsQueueService;
    private final AuditService auditService;
    private final ToshibaEwbService toshibaEwbService;
    private final SnmpMonitoringService snmpMonitoringService;

    public JobService(PrintJobRepository printJobRepository,
                      PrinterRepository printerRepository,
                      UserRepository userRepository,
                      SpoolStorageService spoolStorageService,
                      RawSocketPrinterService rawSocketPrinterService,
                      WindowsQueueService windowsQueueService,
                      AuditService auditService) {
        this(printJobRepository, printerRepository, userRepository, spoolStorageService,
                rawSocketPrinterService, windowsQueueService, auditService, null, null);
    }

    public JobService(PrintJobRepository printJobRepository,
                      PrinterRepository printerRepository,
                      UserRepository userRepository,
                      SpoolStorageService spoolStorageService,
                      RawSocketPrinterService rawSocketPrinterService,
                      WindowsQueueService windowsQueueService,
                      AuditService auditService,
                      ToshibaEwbService toshibaEwbService) {
        this(printJobRepository, printerRepository, userRepository, spoolStorageService,
                rawSocketPrinterService, windowsQueueService, auditService, toshibaEwbService, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public JobService(PrintJobRepository printJobRepository,
                      PrinterRepository printerRepository,
                      UserRepository userRepository,
                      SpoolStorageService spoolStorageService,
                      RawSocketPrinterService rawSocketPrinterService,
                      WindowsQueueService windowsQueueService,
                      AuditService auditService,
                      @org.springframework.beans.factory.annotation.Autowired(required = false) ToshibaEwbService toshibaEwbService,
                      @org.springframework.beans.factory.annotation.Autowired(required = false) SnmpMonitoringService snmpMonitoringService) {
        this.printJobRepository = printJobRepository;
        this.printerRepository = printerRepository;
        this.userRepository = userRepository;
        this.spoolStorageService = spoolStorageService;
        this.rawSocketPrinterService = rawSocketPrinterService;
        this.windowsQueueService = windowsQueueService;
        this.auditService = auditService;
        this.toshibaEwbService = toshibaEwbService;
        this.snmpMonitoringService = snmpMonitoringService;
    }

    /**
     * Retrieve all pending (HELD) jobs owned by the authenticated user's immutable SID.
     */
    @Transactional(readOnly = true)
    public List<JobDto> getPendingJobsForUser(UserEntity user) {
        Set<UUID> seen = new HashSet<>();
        List<PrintJob> result = new ArrayList<>();

        // 1. Direct match by User SID
        List<PrintJob> bySid = printJobRepository.findByUserSidAndStatusOrderBySubmittedAtDesc(user.getUserSid(), JobStatus.HELD);
        for (PrintJob j : bySid) {
            if (seen.add(j.getId())) {
                result.add(j);
            }
        }

        // 2. Direct match by username or user entity ID
        List<PrintJob> allHeld = printJobRepository.findByStatusOrderBySubmittedAtDesc(JobStatus.HELD);
        for (PrintJob j : allHeld) {
            if (seen.contains(j.getId())) continue;
            if ((j.getUser() != null && j.getUser().getId() != null && j.getUser().getId().equals(user.getId())) ||
                j.getUsername().equalsIgnoreCase(user.getUsername())) {
                seen.add(j.getId());
                result.add(j);
            }
        }

        // 3. Admin / Shared Operator cluster (admin, administrator, rr)
        Set<String> adminCluster = Set.of("admin", "administrator", "rr");
        if ("ADMIN".equalsIgnoreCase(user.getRole()) || adminCluster.contains(user.getUsername().toLowerCase())) {
            for (PrintJob j : allHeld) {
                if (!seen.contains(j.getId()) && adminCluster.contains(j.getUsername().toLowerCase())) {
                    seen.add(j.getId());
                    result.add(j);
                }
            }
        }

        return result.stream().map(JobDto::fromEntity).toList();
    }

    /**
     * Retrieve a specific job by ID, enforcing strict user ownership.
     */
    @Transactional(readOnly = true)
    public JobDto getJobById(UUID id, UserEntity user) {
        PrintJob job = printJobRepository.findById(id)
                .orElseThrow(() -> new JobNotFoundException("Print job not found with ID: " + id));

        validateOwnership(job, user);
        return JobDto.fromEntity(job);
    }

    /**
     * Concurrency-safe pull-print release.
     * Prevents race conditions and duplicate printing across multiple touchscreen taps.
     */
    @Transactional
    public ReleaseResponse releaseJob(UUID id, String targetPrinterCode, UserEntity user, String clientIp) {
        return releaseJob(id, targetPrinterCode, user, clientIp, "AUTO", "AUTO");
    }

    @Transactional
    public ReleaseResponse releaseJob(UUID id, String targetPrinterCode, UserEntity user, String clientIp, String drawer, String paperSize) {
        // 1. Atomic Compare-And-Swap (CAS) to acquire release lock
        int updated = printJobRepository.atomicTransitionStatus(id, JobStatus.HELD, JobStatus.RELEASING);
        if (updated == 0) {
            throw new JobConcurrencyException("Job " + id + " is currently being processed or is not in HELD status.");
        }

        LOGGER.info("Lock acquired for job release: " + id + " by " + user.getUsername());

        // 2. Load fresh entity with updated version
        PrintJob job = printJobRepository.findById(id)
                .orElseThrow(() -> new JobNotFoundException("Print job not found with ID: " + id));

        validateOwnership(job, user);

        // 3. Identify physical target printer (PaperCut Station resolution)
        PrinterEntity targetPrinter = resolvePrinter(targetPrinterCode, clientIp);

        // 3.1 Pre-release Hardware Readiness Check via RFC 3805 SNMP (Paper Empty, Jam, Door Open)
        if (snmpMonitoringService != null) {
            SnmpMonitoringService.HardwareReadiness readiness = snmpMonitoringService.checkPrinterHardwareReadiness(targetPrinter, drawer);
            if (!readiness.isReady()) {
                LOGGER.warning("[RELEASE BLOCKED] Printer " + targetPrinter.getName() + " (" + targetPrinter.getIpAddress() +
                        ") is not ready: " + readiness.reason());

                // Keep job safely in HELD status so the user can load paper/clear error and tap Print again
                job.setStatus(JobStatus.HELD);
                job.setErrorMessage(readiness.reason());
                printJobRepository.save(job);

                auditService.logEvent("JOB_RELEASE_BLOCKED", user.getUserSid(), user.getUsername(), id,
                        targetPrinter.getPrinterCode(), clientIp, readiness.reason(), "WARNING");

                return ReleaseResponse.failed(id, job.getDocumentName(), targetPrinter.getPrinterCode(), readiness.reason());
            }
        }

        // 4. Retrieve authentic spool bytes (.SPL or simulated data)
        byte[] spoolBytes;
        try {
            spoolBytes = spoolStorageService.readAndVerifySpoolData(job.getSpoolFilePath(), job.getChecksumSha256());
        } catch (IOException | SecurityException e) {
            LOGGER.severe("Failed to read spool data for job " + id + ": " + e.getMessage());
            job.setStatus(JobStatus.FAILED);
            job.setErrorMessage("Spool data read/integrity failure: " + e.getMessage());
            printJobRepository.save(job);

            auditService.logEvent("JOB_RELEASE_FAILED", user.getUserSid(), user.getUsername(), id,
                    targetPrinter.getPrinterCode(), clientIp, e.getMessage(), "FAILURE");
            return ReleaseResponse.failed(id, job.getDocumentName(), targetPrinter.getPrinterCode(), e.getMessage());
        }

        // 5. Adapt authentic spool data for selected drawer and paper size (if requested)
        byte[] transmitBytes = adaptSpoolForDrawerAndSize(spoolBytes, drawer, paperSize);

        // 6. Transmit spool data directly to the physical printer where user is standing (Port 9100)
        RawSocketPrinterService.PrintTransmissionResult result =
                rawSocketPrinterService.transmitSpoolData(targetPrinter, transmitBytes, job.getDocumentName());

        if (result.success()) {
            job.setStatus(JobStatus.COMPLETED);
            job.setTargetPrinter(targetPrinter);
            job.setReleasedAt(Instant.now());
            job.setErrorMessage(null);
            printJobRepository.save(job);

            // Increment user's central print count (user-wise accounting)
            int pages = job.getPageCount() > 0 ? job.getPageCount() : 1;
            user.setPrintCount(user.getPrintCount() + pages);
            userRepository.save(user);

            // Clean up Windows spooler queue if this was an authentic intercepted Windows job
            if (job.getWindowsJobId() != null) {
                windowsQueueService.cancelWindowsJob(job.getWindowsJobId());
            }

            auditService.logEvent("JOB_RELEASED", user.getUserSid(), user.getUsername(), id,
                    targetPrinter.getPrinterCode(), clientIp,
                    "Released " + job.getDocumentName() + " (" + job.getPageCount() + " pages) to " +
                            targetPrinter.getName() + " (" + targetPrinter.getIpAddress() + ":" + targetPrinter.getRawPort() + "). " + result.message(),
                    "SUCCESS");

            if (result.simulated()) {
                return ReleaseResponse.simulatedSuccess(id, job.getDocumentName(),
                        targetPrinter.getPrinterCode(), targetPrinter.getName());
            } else {
                if (toshibaEwbService != null && targetPrinter != null && targetPrinter.getIpAddress() != null) {
                    String pIp = targetPrinter.getIpAddress();
                    String pUser = targetPrinter.getAdminUsername();
                    String pPass = targetPrinter.getAdminPassword();
                    java.util.concurrent.CompletableFuture.runAsync(() -> {
                        try {
                            Thread.sleep(8000L);
                            toshibaEwbService.clearJobLogs(pIp, pUser, pPass);
                        } catch (Exception ignored) {}
                    });
                }
                return ReleaseResponse.physicalSuccess(id, job.getDocumentName(),
                        targetPrinter.getPrinterCode(), targetPrinter.getName());
            }
        } else {
            job.setStatus(JobStatus.FAILED);
            job.setErrorMessage(result.message());
            printJobRepository.save(job);

            auditService.logEvent("JOB_RELEASE_FAILED", user.getUserSid(), user.getUsername(), id,
                    targetPrinter.getPrinterCode(), clientIp, result.message(), "FAILURE");

            return ReleaseResponse.failed(id, job.getDocumentName(), targetPrinter.getPrinterCode(), result.message());
        }
    }

    /**
     * Cancels a pending job and securely deletes the associated spool file.
     */
    @Transactional
    public void cancelJob(UUID id, UserEntity user, String clientIp) {
        PrintJob job = printJobRepository.findById(id)
                .orElseThrow(() -> new JobNotFoundException("Print job not found with ID: " + id));

        validateOwnership(job, user);

        int updated = printJobRepository.atomicTransitionStatus(id, JobStatus.HELD, JobStatus.CANCELLED);
        if (updated == 0) {
            throw new JobConcurrencyException("Cannot cancel job " + id + ": job is not in HELD status.");
        }

        if (job.getWindowsJobId() != null) {
            windowsQueueService.cancelWindowsJob(job.getWindowsJobId());
        }

        spoolStorageService.deleteSpoolFile(job.getSpoolFilePath());

        auditService.logEvent("JOB_CANCELLED", user.getUserSid(), user.getUsername(), id,
                null, clientIp, "Cancelled job: " + job.getDocumentName(), "SUCCESS");

        LOGGER.info("Job " + id + " cancelled successfully by " + user.getUsername());
    }

    /**
     * Administrator query to view all jobs across the entire queue.
     */
    @Transactional(readOnly = true)
    public List<JobDto> getAllJobsForAdmin() {
        return printJobRepository.findAllByOrderBySubmittedAtDesc().stream()
                .map(JobDto::fromEntity)
                .toList();
    }

    private void validateOwnership(PrintJob job, UserEntity user) {
        if ("ADMIN".equalsIgnoreCase(user.getRole())) {
            return;
        }
        if (job.getUserSid().equalsIgnoreCase(user.getUserSid())) {
            return;
        }
        if (job.getUser() != null && job.getUser().getId() != null && job.getUser().getId().equals(user.getId())) {
            return;
        }
        if (job.getUsername().equalsIgnoreCase(user.getUsername())) {
            return;
        }
        Set<String> adminCluster = Set.of("admin", "administrator", "rr");
        if (adminCluster.contains(user.getUsername().toLowerCase()) &&
            adminCluster.contains(job.getUsername().toLowerCase())) {
            return;
        }
        throw new SecurityException("Access Denied: You do not own print job " + job.getId());
    }

    public PrinterEntity resolvePrinter(String printerCode, String clientIp) {
        if (printerCode != null && !printerCode.isBlank() && !"VIRTUAL-QUEUE".equalsIgnoreCase(printerCode)) {
            Optional<PrinterEntity> byCode = printerRepository.findByPrinterCode(printerCode.trim().toUpperCase());
            if (byCode.isPresent()) {
                return byCode.get();
            }
        }
        // Auto-detect by physical MFP IP address (from EWB terminal HTTP connection)
        if (clientIp != null && !clientIp.isBlank()) {
            String cleanIp = clientIp.replace("/", "").trim();
            for (PrinterEntity p : printerRepository.findAll()) {
                if (cleanIp.equalsIgnoreCase(p.getIpAddress()) || cleanIp.endsWith(p.getIpAddress())) {
                    LOGGER.info("[AUTO-DETECT STATION] Resolved client IP " + clientIp + " to physical MFP: " + p.getName() + " (" + p.getPrinterCode() + ")");
                    return p;
                }
            }
        }
        return getDefaultPrinter();
    }

    public byte[] adaptSpoolForDrawerAndSize(byte[] spoolBytes, String targetDrawer, String targetPaperSize) {
        if (spoolBytes == null || spoolBytes.length < 50) {
            return spoolBytes;
        }

        byte[] modified = java.util.Arrays.copyOf(spoolBytes, spoolBytes.length);

        // 0. Neutralize optional finisher outbin if present so physical sheets drop into standard InnerTray
        byte[] targetOutbin = "@PJL SET OUTBIN=OPTIONALOUTBIN2".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
        byte[] replOutbin   = "@PJL COMMENT OUTBIN=OPTIONALOU2".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
        for (int i = 0; i <= modified.length - targetOutbin.length; i++) {
            boolean match = true;
            for (int j = 0; j < targetOutbin.length; j++) {
                if (modified[i + j] != targetOutbin[j]) {
                    match = false;
                    break;
                }
            }
            if (match) {
                LOGGER.info("[OUTBIN SANITIZE] Replaced OPTIONALOUTBIN2 with PJL comment for InnerTray delivery at offset " + i);
                System.arraycopy(replOutbin, 0, modified, i, replOutbin.length);
                break;
            }
        }

        // 1. PCL XL MediaSource Patching (0xC0 <val> 0xF8 0x26)
        // Standard PCL-XL Protocol Enums (from PCLXL.GPD):
        // 0x01: eAutoSelect
        // 0x02: eManualFeed
        // 0x03: eMultiPurposeTray (Bypass Tray)
        // 0x04: eUpperCassette (Drawer 1)
        // 0x05: eLowerCassette (Drawer 2)
        // 0x07: eThirdCassette (Drawer 3)
        if (targetDrawer != null && !"AUTO".equalsIgnoreCase(targetDrawer.trim())) {
            byte sourceVal;
            String drawerUpper = targetDrawer.trim().toUpperCase();
            if (drawerUpper.contains("DRAWER1") || drawerUpper.contains("TRAY1") || drawerUpper.contains("UPPER")) {
                sourceVal = 0x04; // PCL-XL eUpperCassette (Drawer 1)
            } else if (drawerUpper.contains("DRAWER2") || drawerUpper.contains("TRAY2") || drawerUpper.contains("LOWER")) {
                sourceVal = 0x05; // PCL-XL eLowerCassette (Drawer 2)
            } else if (drawerUpper.contains("DRAWER3") || drawerUpper.contains("TRAY3")) {
                sourceVal = 0x07; // PCL-XL eThirdCassette (Drawer 3)
            } else if (drawerUpper.contains("BYPASS") || drawerUpper.contains("MANUAL")) {
                sourceVal = 0x03; // PCL-XL eMultiPurposeTray (Bypass Tray)
            } else {
                sourceVal = 0x01; // PCL-XL eAutoSelect (Auto Select)
            }

            for (int i = 2; i < modified.length - 1; i++) {
                if ((modified[i] & 0xFF) == 0xF8 && (modified[i + 1] & 0xFF) == 0x26) {
                    if ((modified[i - 2] & 0xFF) == 0xC0) {
                        LOGGER.info("[DRAWER OVERRIDE] Setting PCL XL MediaSource at offset " + (i - 1) + " to 0x" +
                                Integer.toHexString(sourceVal & 0xFF) + " (" + targetDrawer + ")");
                        modified[i - 1] = sourceVal;
                    }
                }
            }
        }

        // 2. PCL XL MediaSize Patching (C8 C1 02 00 41 34 F8 25 for A4, C8 C1 02 00 41 33 F8 25 for A3, etc.)
        if (targetPaperSize != null && !"AUTO".equalsIgnoreCase(targetPaperSize.trim()) && !"ORIGINAL".equalsIgnoreCase(targetPaperSize.trim())) {
            String sizeUpper = targetPaperSize.trim().toUpperCase();
            for (int i = 6; i < modified.length - 1; i++) {
                if ((modified[i] & 0xFF) == 0xF8 && (modified[i + 1] & 0xFF) == 0x25) {
                    if ((modified[i - 6] & 0xFF) == 0xC8 && (modified[i - 5] & 0xFF) == 0xC1 &&
                        (modified[i - 4] & 0xFF) == 0x02 && (modified[i - 3] & 0xFF) == 0x00) {
                        if ("A4".equals(sizeUpper)) {
                            modified[i - 2] = 'A';
                            modified[i - 1] = '4';
                            LOGGER.info("[PAPER SIZE OVERRIDE] Setting PCL XL MediaSize to A4 at offset " + (i - 2));
                        } else if ("A3".equals(sizeUpper)) {
                            modified[i - 2] = 'A';
                            modified[i - 1] = '3';
                            LOGGER.info("[PAPER SIZE OVERRIDE] Setting PCL XL MediaSize to A3 at offset " + (i - 2));
                        }
                    }
                }
            }
        }

        return modified;
    }

    private PrinterEntity getDefaultPrinter() {
        return printerRepository.findAll().stream()
                .filter(PrinterEntity::isDefaultHoldQueue)
                .findFirst()
                .or(() -> printerRepository.findByIsActiveTrue().stream().findFirst())
                .orElseGet(() -> new PrinterEntity("TOSHIBA-3518A-HQ", "Toshiba e-STUDIO 3518A", "192.168.1.150", 9100, "HQ Office", "Toshiba e-STUDIO 3518A", false, true));
    }
}
