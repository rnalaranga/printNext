package com.rr.secureprint.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class CryptoServiceTest {

    private CryptoService cryptoService;

    @BeforeEach
    void setUp() {
        cryptoService = new CryptoService();
        ReflectionTestUtils.setField(cryptoService, "configuredSecretKey", "super-secret-enterprise-key-32bytes!");
        ReflectionTestUtils.setField(cryptoService, "cryptoEnabled", true);
        cryptoService.init();
    }

    @Test
    void shouldEncryptAndDecryptPayloadSuccessfully() {
        byte[] plaintext = "CONFIDENTIAL_PAYROLL_DATA_2026_XYZ_987".getBytes(StandardCharsets.UTF_8);

        byte[] encrypted = cryptoService.encrypt(plaintext);

        assertNotNull(encrypted);
        assertTrue(encrypted.length > plaintext.length);
        assertTrue(cryptoService.isEncrypted(encrypted));

        // Decrypt
        byte[] decrypted = cryptoService.decrypt(encrypted);
        assertArrayEquals(plaintext, decrypted);
        assertEquals("CONFIDENTIAL_PAYROLL_DATA_2026_XYZ_987", new String(decrypted, StandardCharsets.UTF_8));
    }

    @Test
    void shouldHandleLegacyUnencryptedPayloadGracefully() {
        byte[] legacyPlaintext = "LEGACY_PRINT_DATA_NOT_ENCRYPTED".getBytes(StandardCharsets.UTF_8);

        // Not encrypted, should return raw bytes without throwing exception
        assertFalse(cryptoService.isEncrypted(legacyPlaintext));
        byte[] result = cryptoService.decrypt(legacyPlaintext);
        assertArrayEquals(legacyPlaintext, result);
    }

    @Test
    void shouldProduceDifferentCiphertextForSamePlaintextDueToRandomIV() {
        byte[] data = "SAME_DATA_TWICE".getBytes(StandardCharsets.UTF_8);

        byte[] enc1 = cryptoService.encrypt(data);
        byte[] enc2 = cryptoService.encrypt(data);

        // Two encryptions must have different random IVs and different ciphertexts
        assertFalse(java.util.Arrays.equals(enc1, enc2));

        // But both decrypt to the exact same plaintext
        assertArrayEquals(data, cryptoService.decrypt(enc1));
        assertArrayEquals(data, cryptoService.decrypt(enc2));
    }
}
