package com.rr.secureprint.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SpoolStorageServiceTest {

    private SpoolStorageService storageService;

    @TempDir
    Path tempSpoolDir;

    @BeforeEach
    void setUp() throws IOException {
        storageService = new SpoolStorageService();
        ReflectionTestUtils.setField(storageService, "configuredStorageDir", tempSpoolDir.toString());
        storageService.init();
    }

    @Test
    void shouldStoreSpoolDataAndCalculateSha256() throws IOException {
        byte[] testData = "SAMPLE_PCL6_PRINT_BYTES_12345".getBytes(StandardCharsets.UTF_8);

        SpoolStorageService.StoredFileResult result = storageService.storeSpoolData(testData, "test_job");

        assertNotNull(result);
        assertNotNull(result.absolutePath());
        assertNotNull(result.sha256());
        assertEquals(testData.length, result.sizeBytes());

        // Verify reading back matches
        byte[] readBack = storageService.readAndVerifySpoolData(result.absolutePath(), result.sha256());
        assertArrayEquals(testData, readBack);
    }

    @Test
    void shouldFailWhenSha256MismatchOccurs() throws IOException {
        byte[] testData = "AUTHENTIC_PRINT_DOCUMENT".getBytes(StandardCharsets.UTF_8);
        SpoolStorageService.StoredFileResult result = storageService.storeSpoolData(testData, "tamper_test");

        String fraudulentHash = "0000000000000000000000000000000000000000000000000000000000000000";

        assertThrows(SecurityException.class, () -> {
            storageService.readAndVerifySpoolData(result.absolutePath(), fraudulentHash);
        });
    }

    @Test
    void shouldPreventPathTraversalAttempts() {
        String maliciousPath = tempSpoolDir.resolve("../../../Windows/System32/evil.bin").toString();

        assertThrows(SecurityException.class, () -> {
            storageService.readAndVerifySpoolData(maliciousPath, null);
        });
    }
}
