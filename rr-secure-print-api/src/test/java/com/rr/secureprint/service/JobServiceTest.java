package com.rr.secureprint.service;

import com.rr.secureprint.dto.ReleaseResponse;
import com.rr.secureprint.entity.JobStatus;
import com.rr.secureprint.entity.PrintJob;
import com.rr.secureprint.entity.PrinterEntity;
import com.rr.secureprint.entity.UserEntity;
import com.rr.secureprint.exception.JobConcurrencyException;
import com.rr.secureprint.repository.PrintJobRepository;
import com.rr.secureprint.repository.PrinterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JobServiceTest {

    @Mock
    private PrintJobRepository printJobRepository;

    @Mock
    private PrinterRepository printerRepository;

    private AuditService fakeAuditService;
    private SpoolStorageService fakeSpoolStorageService;
    private RawSocketPrinterService fakePrinterService;
    private JobService jobService;

    private UserEntity authorizedUser;
    private UserEntity attackerUser;
    private PrintJob heldJob;
    private PrinterEntity defaultPrinter;

    @BeforeEach
    void setUp() {
        // Safe concrete fakes to avoid JDK 25 dynamic agent bytecode modifications
        fakeSpoolStorageService = new SpoolStorageService() {
            @Override
            public byte[] readAndVerifySpoolData(String filePath, String expectedSha256) {
                return new byte[]{1, 2, 3, 4};
            }
            @Override
            public boolean deleteSpoolFile(String filePath) {
                return true;
            }
        };

        fakePrinterService = new RawSocketPrinterService() {
            @Override
            public PrintTransmissionResult transmitSpoolData(PrinterEntity printer, byte[] spoolData, String jobSummary) {
                return new PrintTransmissionResult(true, true, "Simulated transmission successful");
            }
        };

        fakeAuditService = new AuditService(null) {
            @Override
            public com.rr.secureprint.entity.AuditLog logEvent(String eventType, String userSid, String username, UUID jobId,
                                                               String printerCode, String ipAddress, String actionDetails, String status) {
                return null;
            }
        };

        jobService = new JobService(printJobRepository, printerRepository, fakeSpoolStorageService, fakePrinterService, new WindowsQueueService(), fakeAuditService);

        authorizedUser = new UserEntity("jdoe", "John Doe", "S-1-5-21-1001", "hash", "BADGE-1", "jdoe@rr.local", "Engineering", "USER");
        attackerUser = new UserEntity("mallory", "Mallory", "S-1-5-21-9999", "hash", "BADGE-9", "mallory@rr.local", "Sales", "USER");

        heldJob = new PrintJob(
                UUID.randomUUID(),
                authorizedUser,
                authorizedUser.getUserSid(),
                authorizedUser.getUsername(),
                "Structural-Calculation.pdf",
                5,
                false,
                true,
                1,
                1024L,
                "spool_data/job.bin",
                "checksum",
                "WS-01",
                Instant.now().plusSeconds(3600)
        );

        defaultPrinter = new PrinterEntity("TOSHIBA-3518A-HQ", "Toshiba e-STUDIO 3518A", "192.168.1.150", 9100, "HQ", "3518A", false, true);
    }

    @Test
    void shouldReleaseJobSuccessfullyWhenAuthorized() {
        when(printJobRepository.findById(heldJob.getId())).thenReturn(Optional.of(heldJob));
        when(printJobRepository.atomicTransitionStatus(heldJob.getId(), JobStatus.HELD, JobStatus.RELEASING)).thenReturn(1);
        when(printerRepository.findByPrinterCode(any())).thenReturn(Optional.of(defaultPrinter));

        ReleaseResponse response = jobService.releaseJob(heldJob.getId(), "TOSHIBA-3518A-HQ", authorizedUser, "127.0.0.1");

        assertTrue(response.success());
        assertTrue(response.simulated());
        assertEquals(heldJob.getId(), response.jobId());
        verify(printJobRepository).save(heldJob);
        assertEquals(JobStatus.COMPLETED, heldJob.getStatus());
    }

    @Test
    void shouldRejectReleaseWhenAttemptedByUnauthorizedUser() {
        when(printJobRepository.atomicTransitionStatus(heldJob.getId(), JobStatus.HELD, JobStatus.RELEASING)).thenReturn(1);
        when(printJobRepository.findById(heldJob.getId())).thenReturn(Optional.of(heldJob));

        assertThrows(SecurityException.class, () -> {
            jobService.releaseJob(heldJob.getId(), "TOSHIBA-3518A-HQ", attackerUser, "127.0.0.1");
        });
    }

    @Test
    void shouldThrowConcurrencyExceptionWhenJobAlreadyReleasing() {
        // Atomic transition returns 0 -> another thread won the race
        when(printJobRepository.atomicTransitionStatus(heldJob.getId(), JobStatus.HELD, JobStatus.RELEASING)).thenReturn(0);

        assertThrows(JobConcurrencyException.class, () -> {
            jobService.releaseJob(heldJob.getId(), "TOSHIBA-3518A-HQ", authorizedUser, "127.0.0.1");
        });
    }

    @Test
    void shouldCancelJobSuccessfullyAndPurgeSpool() {
        when(printJobRepository.findById(heldJob.getId())).thenReturn(Optional.of(heldJob));
        when(printJobRepository.atomicTransitionStatus(heldJob.getId(), JobStatus.HELD, JobStatus.CANCELLED)).thenReturn(1);

        jobService.cancelJob(heldJob.getId(), authorizedUser, "127.0.0.1");

        verify(printJobRepository).atomicTransitionStatus(heldJob.getId(), JobStatus.HELD, JobStatus.CANCELLED);
    }
}
