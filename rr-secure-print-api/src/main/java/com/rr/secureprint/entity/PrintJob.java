package com.rr.secureprint.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "print_jobs")
public class PrintJob {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "job_number", insertable = false, updatable = false)
    private Long jobNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private UserEntity user;

    @Column(name = "user_sid", nullable = false, length = 128)
    private String userSid;

    @Column(nullable = false, length = 64)
    private String username;

    @Column(name = "document_name", nullable = false)
    private String documentName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private JobStatus status = JobStatus.RECEIVED;

    @Column(name = "page_count", nullable = false)
    private int pageCount = 1;

    @Column(nullable = false)
    private boolean color = false;

    @Column(nullable = false)
    private boolean duplex = true;

    @Column(nullable = false)
    private int copies = 1;

    @Column(name = "file_size_bytes", nullable = false)
    private long fileSizeBytes = 0;

    @Column(name = "spool_file_path", nullable = false, length = 512)
    private String spoolFilePath;

    @Column(name = "checksum_sha256", nullable = false, length = 64)
    private String checksumSha256;

    @Column(name = "client_machine", length = 128)
    private String clientMachine;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_printer_id")
    private PrinterEntity targetPrinter;

    @Column(name = "windows_job_id")
    private Integer windowsJobId;

    @Column(name = "submitted_at")
    private Instant submittedAt = Instant.now();

    @Column(name = "released_at")
    private Instant releasedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Version
    @Column(nullable = false)
    private Long version = 0L;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    public PrintJob() {}

    public PrintJob(UUID id, UserEntity user, String userSid, String username, String documentName,
                    int pageCount, boolean color, boolean duplex, int copies, long fileSizeBytes,
                    String spoolFilePath, String checksumSha256, String clientMachine, Instant expiresAt) {
        this.id = id != null ? id : UUID.randomUUID();
        this.user = user;
        this.userSid = userSid;
        this.username = username;
        this.documentName = documentName;
        this.status = JobStatus.HELD;
        this.pageCount = pageCount;
        this.color = color;
        this.duplex = duplex;
        this.copies = copies;
        this.fileSizeBytes = fileSizeBytes;
        this.spoolFilePath = spoolFilePath;
        this.checksumSha256 = checksumSha256;
        this.clientMachine = clientMachine;
        this.submittedAt = Instant.now();
        this.expiresAt = expiresAt;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Long getJobNumber() { return jobNumber; }
    public void setJobNumber(Long jobNumber) { this.jobNumber = jobNumber; }

    public UserEntity getUser() { return user; }
    public void setUser(UserEntity user) { this.user = user; }

    public String getUserSid() { return userSid; }
    public void setUserSid(String userSid) { this.userSid = userSid; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getDocumentName() { return documentName; }
    public void setDocumentName(String documentName) { this.documentName = documentName; }

    public JobStatus getStatus() { return status; }
    public void setStatus(JobStatus status) { this.status = status; }

    public int getPageCount() { return pageCount; }
    public void setPageCount(int pageCount) { this.pageCount = pageCount; }

    public boolean isColor() { return color; }
    public void setColor(boolean color) { this.color = color; }

    public boolean isDuplex() { return duplex; }
    public void setDuplex(boolean duplex) { this.duplex = duplex; }

    public int getCopies() { return copies; }
    public void setCopies(int copies) { this.copies = copies; }

    public long getFileSizeBytes() { return fileSizeBytes; }
    public void setFileSizeBytes(long fileSizeBytes) { this.fileSizeBytes = fileSizeBytes; }

    public String getSpoolFilePath() { return spoolFilePath; }
    public void setSpoolFilePath(String spoolFilePath) { this.spoolFilePath = spoolFilePath; }

    public String getChecksumSha256() { return checksumSha256; }
    public void setChecksumSha256(String checksumSha256) { this.checksumSha256 = checksumSha256; }

    public String getClientMachine() { return clientMachine; }
    public void setClientMachine(String clientMachine) { this.clientMachine = clientMachine; }

    public PrinterEntity getTargetPrinter() { return targetPrinter; }
    public void setTargetPrinter(PrinterEntity targetPrinter) { this.targetPrinter = targetPrinter; }

    public Instant getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(Instant submittedAt) { this.submittedAt = submittedAt; }

    public Instant getReleasedAt() { return releasedAt; }
    public void setReleasedAt(Instant releasedAt) { this.releasedAt = releasedAt; }

    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public Integer getWindowsJobId() { return windowsJobId; }
    public void setWindowsJobId(Integer windowsJobId) { this.windowsJobId = windowsJobId; }
}
