package com.rr.secureprint.dto;

import com.rr.secureprint.entity.ScanJobEntity;
import java.time.Instant;

public record ScanJobDto(
        Long id,
        Long userId,
        Long printerId,
        String username,
        String printerName,
        String printerIp,
        String recipientEmail,
        String subject,
        String fileName,
        int pages,
        String colorMode,
        String resolution,
        String fileFormat,
        String duplex,
        String status,
        String deliveryMessage,
        Instant completedAt,
        Instant createdAt
) {
    public static ScanJobDto fromEntity(ScanJobEntity entity) {
        if (entity == null) return null;
        return new ScanJobDto(
                entity.getId(),
                entity.getUser() != null ? entity.getUser().getId() : null,
                entity.getPrinter() != null ? entity.getPrinter().getId() : null,
                entity.getUsername(),
                entity.getPrinterName(),
                entity.getPrinterIp(),
                entity.getRecipientEmail(),
                entity.getSubject(),
                entity.getFileName(),
                entity.getPages(),
                entity.getColorMode(),
                entity.getResolution(),
                entity.getFileFormat(),
                entity.getDuplex(),
                entity.getStatus(),
                entity.getDeliveryMessage(),
                entity.getCompletedAt(),
                entity.getCreatedAt()
        );
    }
}
