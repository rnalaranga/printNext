package com.rr.secureprint.service;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.logging.Logger;

/**
 * Enterprise Cryptographic Service for nexPrint.
 * Provides authenticated AES-256-GCM encryption at rest for:
 * 1. Quarantined Print Spool Files (.bin / .spl) in spool_data/
 * 2. Scanned PDF Documents archived in data/scans/
 *
 * Header Format:
 * [4 bytes Magic "NEXP"] + [12 bytes Random IV] + [AES-256-GCM Ciphertext + 128-bit Tag]
 */
@Service
public class CryptoService {

    private static final Logger LOGGER = Logger.getLogger(CryptoService.class.getName());

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final byte[] MAGIC_HEADER = new byte[]{'N', 'E', 'X', 'P'};

    private final SecureRandom secureRandom = new SecureRandom();
    private SecretKeySpec secretKeySpec;

    @Value("${app.crypto.secret-key:nexPrint-Enterprise-Master-Secret-2026-AES256-GCM-Key}")
    private String configuredSecretKey;

    @Value("${app.crypto.enabled:true}")
    private boolean cryptoEnabled;

    @PostConstruct
    public void init() {
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            byte[] keyBytes = sha256.digest(configuredSecretKey.getBytes(StandardCharsets.UTF_8));
            this.secretKeySpec = new SecretKeySpec(keyBytes, "AES");
            LOGGER.info("[CRYPTO] Initialized AES-256-GCM encryption engine (Enabled: " + cryptoEnabled + ")");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Failed to initialize SHA-256 for AES key derivation", e);
        }
    }

    /**
     * Encrypts plaintext bytes using AES-256-GCM with a fresh random 12-byte IV.
     * Output format: [NEXP (4 bytes)] + [IV (12 bytes)] + [Ciphertext + Auth Tag]
     */
    public byte[] encrypt(byte[] plaintext) {
        if (!cryptoEnabled || plaintext == null || plaintext.length == 0) {
            return plaintext;
        }

        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, spec);

            byte[] cipherBytes = cipher.doFinal(plaintext);

            // Construct payload: Magic (4) + IV (12) + Ciphertext
            byte[] payload = new byte[MAGIC_HEADER.length + GCM_IV_LENGTH + cipherBytes.length];
            System.arraycopy(MAGIC_HEADER, 0, payload, 0, MAGIC_HEADER.length);
            System.arraycopy(iv, 0, payload, MAGIC_HEADER.length, GCM_IV_LENGTH);
            System.arraycopy(cipherBytes, 0, payload, MAGIC_HEADER.length + GCM_IV_LENGTH, cipherBytes.length);

            return payload;
        } catch (Exception e) {
            LOGGER.severe("[CRYPTO] Encryption failed: " + e.getMessage());
            throw new SecurityException("Failed to encrypt document data: " + e.getMessage(), e);
        }
    }

    /**
     * Decrypts an encrypted payload.
     * If the payload does not start with the magic header "NEXP", it is treated as a legacy
     * unencrypted file and returned as-is for backward compatibility.
     */
    public byte[] decrypt(byte[] payload) {
        if (payload == null || payload.length == 0) {
            return payload;
        }

        // Backward compatibility check: must start with 'NEXP' and have minimum length
        if (!isEncrypted(payload)) {
            LOGGER.fine("[CRYPTO] Payload is unencrypted; returning raw bytes.");
            return payload;
        }

        try {
            int ivOffset = MAGIC_HEADER.length;
            int cipherOffset = ivOffset + GCM_IV_LENGTH;
            int cipherLength = payload.length - cipherOffset;

            byte[] iv = Arrays.copyOfRange(payload, ivOffset, cipherOffset);
            byte[] cipherBytes = Arrays.copyOfRange(payload, cipherOffset, payload.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, spec);

            return cipher.doFinal(cipherBytes);
        } catch (Exception e) {
            LOGGER.severe("[CRYPTO] Decryption / Authentication failed: " + e.getMessage());
            throw new SecurityException("Failed to decrypt document payload (Integrity or Key Mismatch): " + e.getMessage(), e);
        }
    }

    /**
     * Checks if a byte array begins with the nexPrint encryption magic header ("NEXP").
     */
    public boolean isEncrypted(byte[] payload) {
        if (payload == null || payload.length < (MAGIC_HEADER.length + GCM_IV_LENGTH + 16)) {
            return false;
        }
        for (int i = 0; i < MAGIC_HEADER.length; i++) {
            if (payload[i] != MAGIC_HEADER[i]) {
                return false;
            }
        }
        return true;
    }

    public boolean isCryptoEnabled() {
        return cryptoEnabled;
    }
}
