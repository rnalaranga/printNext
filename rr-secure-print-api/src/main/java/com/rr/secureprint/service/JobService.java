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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

@Service
public class JobService {

    private static final Logger LOGGER = Logger.getLogger(JobService.class.getName());

    private final PrintJobRepository printJobRepository;
    private final PrinterRepository printerRepository;
    private final SpoolStorageService spoolStorageService;
    private final RawSocketPrinterService rawSocketPrinterService;
    private final WindowsQueueService windowsQueueService;
    private final AuditService auditService;

    public JobService(PrintJobRepository printJobRepository,
                      PrinterRepository printerRepository,
                      SpoolStorageService spoolStorageService,
                      RawSocketPrinterService rawSocketPrinterService,
                      WindowsQueueService windowsQueueService,
                      AuditService auditService) {
        this.printJobRepository = printJobRepository;
        this.printerRepository = printerRepository;
        this.spoolStorageService = spoolStorageService;
        this.rawSocketPrinterService = rawSocketPrinterService;
        this.windowsQueueService = windowsQueueService;
        this.auditService = auditService;
    }

    /**
     * Retrieve all pending (HELD) jobs owned by the authenticated user's immutable SID.
     */
    @Transactional(readOnly = true)
    public List<JobDto> getPendingJobsForUser(UserEntity user) {
        List<PrintJob> jobs = printJobRepository.findByUserSidAndStatusOrderBySubmittedAtDesc(user.getUserSid(), JobStatus.HELD);
        return jobs.stream().map(JobDto::fromEntity).toList();
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

        // 3. Identify target printer
        PrinterEntity targetPrinter = resolvePrinter(targetPrinterCode);

        // 4. If this job is held in Windows Spooler, release it via Windows Spooler directly to physical printer
        if (job.getWindowsJobId() != null) {
            LOGGER.info("Releasing authentic Windows Spool job #" + job.getWindowsJobId() + " to " + targetPrinter.getPrinterCode());
            boolean released = windowsQueueService.releaseWindowsJob(job.getWindowsJobId());
            if (released) {
                job.setStatus(JobStatus.COMPLETED);
                job.setTargetPrinter(targetPrinter);
                job.setReleasedAt(Instant.now());
                job.setErrorMessage(null);
                printJobRepository.save(job);

                auditService.logEvent("JOB_RELEASED", user.getUserSid(), user.getUsername(), id,
                        targetPrinter.getPrinterCode(), clientIp,
                        "Released Windows Job #" + job.getWindowsJobId() + " (" + job.getDocumentName() + ") to " + targetPrinter.getIpAddress() + ":" + targetPrinter.getRawPort(),
                        "SUCCESS");

                return ReleaseResponse.physicalSuccess(id, job.getDocumentName(),
                        targetPrinter.getPrinterCode(), targetPrinter.getName());
            } else {
                job.setStatus(JobStatus.FAILED);
                job.setErrorMessage("Windows spooler failed to transmit job #" + job.getWindowsJobId());
                printJobRepository.save(job);

                auditService.logEvent("JOB_RELEASE_FAILED", user.getUserSid(), user.getUsername(), id,
                        targetPrinter.getPrinterCode(), clientIp, "Windows spooler release failed", "FAILURE");

                return ReleaseResponse.failed(id, job.getDocumentName(), targetPrinter.getPrinterCode(),
                        "Windows spooler failed to transmit job #" + job.getWindowsJobId());
            }
        }

        // 5. Fallback for simulated or direct spool storage jobs
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

        // 6. Transmit spool bytes (either simulated or Port 9100 live socket)
        RawSocketPrinterService.PrintTransmissionResult result =
                rawSocketPrinterService.transmitSpoolData(targetPrinter, spoolBytes, job.getDocumentName());

        if (result.success()) {
            job.setStatus(JobStatus.COMPLETED);
            job.setTargetPrinter(targetPrinter);
            job.setReleasedAt(Instant.now());
            job.setErrorMessage(null);
            printJobRepository.save(job);

            auditService.logEvent("JOB_RELEASED", user.getUserSid(), user.getUsername(), id,
                    targetPrinter.getPrinterCode(), clientIp,
                    "Released " + job.getDocumentName() + " (" + job.getPageCount() + " pages). " + result.message(),
                    "SUCCESS");

            if (result.simulated()) {
                return ReleaseResponse.simulatedSuccess(id, job.getDocumentName(),
                        targetPrinter.getPrinterCode(), targetPrinter.getName());
            } else {
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
        if (!"ADMIN".equalsIgnoreCase(user.getRole()) && !job.getUserSid().equalsIgnoreCase(user.getUserSid())) {
            throw new SecurityException("Access Denied: You do not own print job " + job.getId());
        }
    }

    private PrinterEntity resolvePrinter(String printerCode) {
        if (printerCode != null && !printerCode.isBlank()) {
            return printerRepository.findByPrinterCode(printerCode)
                    .orElseGet(this::getDefaultPrinter);
        }
        return getDefaultPrinter();
    }

    private PrinterEntity getDefaultPrinter() {
        return printerRepository.findByIsActiveTrue().stream().findFirst()
                .orElseGet(() -> new PrinterEntity("TOSHIBA-3518A-HQ", "Toshiba e-STUDIO 3518A", "192.168.1.150", 9100, "HQ Office", "Toshiba e-STUDIO 3518A", false, true));
    }
}
