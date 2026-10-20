package com.rr.secureprint.agent.model;

import java.io.Serializable;
import java.time.Instant;

/**
 * Metadata captured from Windows Print Spooler (.SHD shadow file or Win32 JOB_INFO_2).
 */
public record SpoolJobMetadata(
        int windowsJobId,
        String printerName,
        String documentName,
        String userName,
        String userSid,
        String dataType,
        int totalPages,
        long sizeBytes,
        Instant submittedTime,
        String originalSpoolPath
) implements Serializable {
    public SpoolJobMetadata {
        if (documentName == null || documentName.isBlank()) {
            documentName = "Untitled Document";
        }
        if (userName == null || userName.isBlank()) {
            userName = "UNKNOWN_USER";
        }
        if (userSid == null || userSid.isBlank()) {
            userSid = "S-1-0-0";
        }
    }
}
