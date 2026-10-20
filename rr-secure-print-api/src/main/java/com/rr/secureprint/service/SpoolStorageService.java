package com.rr.secureprint.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Manages secure file storage for printer-ready spool files (.SPL / RAW data).
 * Isolates spool files from the web root and database, enforces directory traversal prevention,
 * and maintains SHA-256 cryptographic checksums for document integrity.
 */
@Service
public class SpoolStorageService {

    private static final Logger LOGGER = Logger.getLogger(SpoolStorageService.class.getName());

    @Value("${app.spool.storage-dir:spool_data}")
    private String configuredStorageDir;

    private Path rootStoragePath;

    @PostConstruct
    public void init() throws IOException {
        rootStoragePath = Paths.get(configuredStorageDir).toAbsolutePath().normalize();
        if (!Files.exists(rootStoragePath)) {
            Files.createDirectories(rootStoragePath);
            LOGGER.info("Created secure spool storage directory: " + rootStoragePath);
        } else {
            LOGGER.info("Using existing spool storage directory: " + rootStoragePath);
        }
    }

    /**
     * Stores spool bytes safely into a uniquely named binary file.
     * Prevents path traversal and returns the relative path and SHA-256 checksum.
     */
    public StoredFileResult storeSpoolData(byte[] data, String filePrefix) throws IOException {
        String safePrefix = (filePrefix != null && !filePrefix.isBlank())
                ? filePrefix.replaceAll("[^a-zA-Z0-9_-]", "") : "job";
        String fileName = safePrefix + "_" + UUID.randomUUID() + ".bin";

        Path targetPath = rootStoragePath.resolve(fileName).normalize();

        // Enforce path traversal protection
        if (!targetPath.startsWith(rootStoragePath)) {
            throw new SecurityException("Potential directory traversal attempt detected in spool filename!");
        }

        Files.write(targetPath, data, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        String sha256 = calculateSha256(data);

        LOGGER.info("Stored spool file: " + targetPath.getFileName() + " (" + data.length + " bytes, SHA256: " + sha256 + ")");
        return new StoredFileResult(targetPath.toString(), sha256, data.length);
    }

    /**
     * Reads the spool file bytes while verifying its SHA-256 checksum against database records.
     */
    public byte[] readAndVerifySpoolData(String filePathString, String expectedSha256) throws IOException {
        Path filePath = Paths.get(filePathString).toAbsolutePath().normalize();

        // Enforce path containment
        if (!filePath.startsWith(rootStoragePath)) {
            throw new SecurityException("Spool file path is outside the authorized storage boundary!");
        }

        if (!Files.exists(filePath)) {
            throw new FileNotFoundException("Spool file not found on disk: " + filePath.getFileName());
        }

        byte[] bytes = Files.readAllBytes(filePath);
        String actualSha256 = calculateSha256(bytes);

        if (expectedSha256 != null && !expectedSha256.equalsIgnoreCase(actualSha256)) {
            throw new SecurityException("Spool integrity violation: SHA-256 mismatch! File may have been altered.");
        }

        return bytes;
    }

    /**
     * Safely deletes a spool file upon completion, cancellation, or expiry.
     */
    public boolean deleteSpoolFile(String filePathString) {
        if (filePathString == null || filePathString.isBlank()) {
            return false;
        }
        try {
            Path filePath = Paths.get(filePathString).toAbsolutePath().normalize();
            if (filePath.startsWith(rootStoragePath) && Files.exists(filePath)) {
                Files.delete(filePath);
                LOGGER.info("Deleted spool file: " + filePath.getFileName());
                return true;
            }
        } catch (Exception e) {
            LOGGER.warning("Could not delete spool file " + filePathString + ": " + e.getMessage());
        }
        return false;
    }

    public String calculateSha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm unavailable", e);
        }
    }

    public Path getRootStoragePath() {
        return rootStoragePath;
    }

    public record StoredFileResult(String absolutePath, String sha256, long sizeBytes) {}
}
