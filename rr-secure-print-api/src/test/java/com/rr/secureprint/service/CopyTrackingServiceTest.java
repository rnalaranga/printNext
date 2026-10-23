package com.rr.secureprint.service;

import com.rr.secureprint.dto.CopyJobDto;
import com.rr.secureprint.entity.CopyJobEntity;
import com.rr.secureprint.entity.PrinterEntity;
import com.rr.secureprint.entity.UserEntity;
import com.rr.secureprint.repository.CopyJobRepository;
import com.rr.secureprint.repository.PrinterRepository;
import com.rr.secureprint.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CopyTrackingServiceTest {

    @Mock
    private CopyJobRepository copyJobRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PrinterRepository printerRepository;

    @Mock
    private AuditService auditService;

    @Mock
    private ToshibaEwbService toshibaEwbService;

    private CopyTrackingService copyTrackingService;

    @BeforeEach
    void setUp() {
        copyTrackingService = new CopyTrackingService(copyJobRepository, userRepository, printerRepository, auditService, toshibaEwbService);
    }

    @Test
    void testRecordCopyJob_IncrementsUserCopyCount() {
        UserEntity user = new UserEntity("kamal", "Kamal Perera", "S-1-5-21-1234", "hash", "BADGE-1", "kamal@rr.local", "Engineering", "USER");
        user.setCopyCount(10);

        when(copyJobRepository.existsByPrinterIpAndPrinterJobId(anyString(), anyString())).thenReturn(false);
        when(userRepository.findByUsernameIgnoreCase("kamal")).thenReturn(Optional.of(user));
        when(copyJobRepository.save(any(CopyJobEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        CopyJobEntity result = copyTrackingService.recordCopyJob(
                "QOA\\kamal", "192.168.1.10", "Toshiba e-STUDIO 2518A",
                5, 0, 5, "JOB-999", Instant.now()
        );

        assertNotNull(result);
        assertEquals(15, user.getCopyCount());
        verify(userRepository).save(user);
        verify(copyJobRepository).save(any(CopyJobEntity.class));
    }

    @Test
    void testRecordCopyJob_IdempotentOnDuplicateJobId() {
        when(copyJobRepository.existsByPrinterIpAndPrinterJobId("192.168.1.10", "JOB-DUPLICATE")).thenReturn(true);

        CopyJobEntity result = copyTrackingService.recordCopyJob(
                "kamal", "192.168.1.10", "Toshiba 2518A",
                5, 0, 5, "JOB-DUPLICATE", Instant.now()
        );

        assertNull(result);
        verify(userRepository, never()).save(any());
        verify(copyJobRepository, never()).save(any());
    }

    @Test
    void testRecordCopyJob_NormalizesUpnUsername() {
        UserEntity user = new UserEntity("nimal", "Nimal Fernando", "S-1-5-21-5678", "hash", "BADGE-2", "nimal@rr.local", "Finance", "USER");
        user.setCopyCount(0);

        when(copyJobRepository.existsByPrinterIpAndPrinterJobId(anyString(), anyString())).thenReturn(false);
        when(userRepository.findByUsernameIgnoreCase("nimal")).thenReturn(Optional.of(user));
        when(copyJobRepository.save(any(CopyJobEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        copyTrackingService.recordCopyJob(
                "nimal@qoa.local", "192.168.1.11", "Toshiba e-STUDIO 4508A",
                3, 1, 2, "JOB-1002", Instant.now()
        );

        assertEquals(3, user.getCopyCount());
        verify(userRepository).save(user);
    }

    @Test
    void testResolveTerminalUserForJob_MatchesSessionTimeWindow() {
        copyTrackingService.registerActiveTerminalUser("192.168.1.10", "chiran");
        Instant jobTime = Instant.now();
        // User logs out right after copying
        copyTrackingService.recordTerminalLogout("192.168.1.10");

        // Job that completed during that session window should resolve to chiran
        String resolved = copyTrackingService.resolveTerminalUserForJob("192.168.1.10", jobTime);
        assertEquals("chiran", resolved);
    }

    @Test
    void testResolveTerminalUserForJob_FallsBackWhenNoSession() {
        String resolved = copyTrackingService.resolveTerminalUserForJob("192.168.1.99", Instant.now());
        assertNull(resolved);
    }
}
