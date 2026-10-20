package com.rr.secureprint.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_logs")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Instant timestamp = Instant.now();

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(name = "user_sid", length = 128)
    private String userSid;

    @Column(length = 64)
    private String username;

    @Column(name = "job_id")
    private UUID jobId;

    @Column(name = "printer_code", length = 64)
    private String printerCode;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "action_details", columnDefinition = "TEXT")
    private String actionDetails;

    @Column(nullable = false, length = 32)
    private String status = "SUCCESS";

    public AuditLog() {}

    public AuditLog(String eventType, String userSid, String username, UUID jobId, String printerCode, String ipAddress, String actionDetails, String status) {
        this.timestamp = Instant.now();
        this.eventType = eventType;
        this.userSid = userSid;
        this.username = username;
        this.jobId = jobId;
        this.printerCode = printerCode;
        this.ipAddress = ipAddress;
        this.actionDetails = actionDetails;
        this.status = status != null ? status : "SUCCESS";
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public String getUserSid() { return userSid; }
    public void setUserSid(String userSid) { this.userSid = userSid; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public UUID getJobId() { return jobId; }
    public void setJobId(UUID jobId) { this.jobId = jobId; }

    public String getPrinterCode() { return printerCode; }
    public void setPrinterCode(String printerCode) { this.printerCode = printerCode; }

    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }

    public String getActionDetails() { return actionDetails; }
    public void setActionDetails(String actionDetails) { this.actionDetails = actionDetails; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
