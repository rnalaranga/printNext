package com.rr.secureprint.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

class SpoolStorageServiceTest {

    private SpoolStorageService storageService;
    private CryptoService cryptoService;

    @TempDir
    Path tempSpoolDir;

    @BeforeEach
    void setUp() throws IOException {
        cryptoService = new CryptoService();
        ReflectionTestUtils.setField(cryptoService, "configuredSecretKey", "test-secret-key-1234567890-test-key");
        ReflectionTestUtils.setField(cryptoService, "cryptoEnabled", true);
        cryptoService.init();

        storageService = new SpoolStorageService(cryptoService);
        ReflectionTestUtils.setField(storageService, "configuredStorageDir", tempSpoolDir.toString());
        storageService.init();
    }

    @Test
    void shouldStoreSpoolDataEncryptedAndReadBackDecrypted() throws IOException {
        byte[] testData = "SAMPLE_PCL6_PRINT_BYTES_12345".getBytes(StandardCharsets.UTF_8);

        SpoolStorageService.StoredFileResult result = storageService.storeSpoolData(testData, "test_job");

        assertNotNull(result);
        assertNotNull(result.absolutePath());
        assertNotNull(result.sha256());
        assertEquals(testData.length, result.sizeBytes());

        // 1. Verify that the raw file stored on disk is encrypted (starts with magic NEXP)
        byte[] rawDiskBytes = Files.readAllBytes(Paths.get(result.absolutePath()));
        assertTrue(cryptoService.isEncrypted(rawDiskBytes), "File on disk must be AES-256-GCM encrypted with NEXP header");
        assertFalse(new String(rawDiskBytes, StandardCharsets.ISO_8859_1).contains("SAMPLE_PCL6_PRINT_BYTES"),
                "Plaintext must not be visible on raw disk storage!");

        // 2. Verify reading back decrypts authentic plaintext accurately
        byte[] readBack = storageService.readAndVerifySpoolData(result.absolutePath(), result.sha256());
        assertArrayEquals(testData, readBack, "Decrypted bytes must match original plaintext");
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
