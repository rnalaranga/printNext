package com.rr.secureprint.dto;

import com.rr.secureprint.entity.AuditLog;

import java.time.Instant;
import java.util.UUID;

public record AuditLogDto(
        Long id,
        Instant timestamp,
        String eventType,
        String userSid,
        String username,
        UUID jobId,
        String printerCode,
        String ipAddress,
        String actionDetails,
        String status
) {
    public static AuditLogDto fromEntity(AuditLog log) {
        return new AuditLogDto(
                log.getId(),
                log.getTimestamp(),
                log.getEventType(),
                log.getUserSid(),
                log.getUsername(),
                log.getJobId(),
                log.getPrinterCode(),
                log.getIpAddress(),
                log.getActionDetails(),
                log.getStatus()
        );
    }
}
