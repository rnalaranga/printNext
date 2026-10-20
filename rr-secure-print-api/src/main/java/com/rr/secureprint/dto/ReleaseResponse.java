package com.rr.secureprint.dto;

import java.time.Instant;
import java.util.UUID;

public record ReleaseResponse(
        boolean success,
        UUID jobId,
        String documentName,
        String printerCode,
        String printerName,
        String message,
        boolean simulated,
        Instant releasedAt
) {
    public static ReleaseResponse simulatedSuccess(UUID jobId, String documentName, String printerCode, String printerName) {
        return new ReleaseResponse(
                true,
                jobId,
                documentName,
                printerCode,
                printerName,
                "SIMULATED RELEASE SUCCESSFUL: Job held data processed and released to virtual queue (No physical output sent).",
                true,
                Instant.now()
        );
    }

    public static ReleaseResponse physicalSuccess(UUID jobId, String documentName, String printerCode, String printerName) {
        return new ReleaseResponse(
                true,
                jobId,
                documentName,
                printerCode,
                printerName,
                "Job transmitted to physical printer successfully.",
                false,
                Instant.now()
        );
    }

    public static ReleaseResponse failed(UUID jobId, String documentName, String printerCode, String message) {
        return new ReleaseResponse(
                false,
                jobId,
                documentName,
                printerCode,
                null,
                message,
                false,
                null
        );
    }
}
