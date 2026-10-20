package com.rr.secureprint.service;

import com.rr.secureprint.entity.AuditLog;
import com.rr.secureprint.repository.AuditLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.logging.Logger;

@Service
public class AuditService {

    private static final Logger LOGGER = Logger.getLogger(AuditService.class.getName());
    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional
    public AuditLog logEvent(String eventType, String userSid, String username, UUID jobId,
                             String printerCode, String ipAddress, String actionDetails, String status) {
        AuditLog log = new AuditLog(
                eventType,
                userSid,
                username,
                jobId,
                printerCode,
                ipAddress,
                actionDetails,
                status != null ? status : "SUCCESS"
        );
        AuditLog saved = auditLogRepository.save(log);
        LOGGER.info(String.format("[AUDIT] %s | User: %s (%s) | Job: %s | Printer: %s | Status: %s | %s",
                eventType, username, userSid, jobId, printerCode, status, actionDetails));
        return saved;
    }
}
