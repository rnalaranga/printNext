package com.rr.secureprint.dto;

import com.rr.secureprint.entity.CopyJobEntity;
import java.time.Instant;

public record CopyJobDto(
        Long id,
        String username,
        String printerName,
        String printerIp,
        int pages,
        int colorPages,
        int monoPages,
        String printerJobId,
        Instant completedAt,
        Instant createdAt
) {
    public static CopyJobDto fromEntity(CopyJobEntity entity) {
        return new CopyJobDto(
                entity.getId(),
                entity.getUsername(),
                entity.getPrinterName(),
                entity.getPrinterIp(),
                entity.getPages(),
                entity.getColorPages(),
                entity.getMonoPages(),
                entity.getPrinterJobId(),
                entity.getCompletedAt(),
                entity.getCreatedAt()
        );
    }
}
