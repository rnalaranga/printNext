package com.rr.secureprint.dto;

import com.rr.secureprint.entity.JobStatus;
import com.rr.secureprint.entity.PrintJob;

import java.time.Instant;
import java.util.UUID;

public record JobDto(
        UUID id,
        Long jobNumber,
        String documentName,
        JobStatus status,
        int pageCount,
        boolean color,
        boolean duplex,
        int copies,
        long fileSizeBytes,
        String formattedFileSize,
        String clientMachine,
        String username,
        String userSid,
        Instant submittedAt,
        Instant releasedAt,
        Instant expiresAt,
        String targetPrinterCode,
        String targetPrinterName,
        boolean simulated
) {
    public static JobDto fromEntity(PrintJob job) {
        String printerCode = job.getTargetPrinter() != null ? job.getTargetPrinter().getPrinterCode() : "ANY-TOSHIBA";
        String printerName = job.getTargetPrinter() != null ? job.getTargetPrinter().getName() : "Toshiba Pull Queue";
        boolean isSimulated = job.getSpoolFilePath() != null && job.getSpoolFilePath().contains("simulated");

        return new JobDto(
                job.getId(),
                job.getJobNumber(),
                job.getDocumentName(),
                job.getStatus(),
                job.getPageCount(),
                job.isColor(),
                job.isDuplex(),
                job.getCopies(),
                job.getFileSizeBytes(),
                formatBytes(job.getFileSizeBytes()),
                job.getClientMachine(),
                job.getUsername(),
                job.getUserSid(),
                job.getSubmittedAt(),
                job.getReleasedAt(),
                job.getExpiresAt(),
                printerCode,
                printerName,
                isSimulated
        );
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        char pre = "KMGTPE".charAt(exp - 1);
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }
}
