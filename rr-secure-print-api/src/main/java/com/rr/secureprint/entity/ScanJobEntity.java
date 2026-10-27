package com.rr.secureprint.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "scan_jobs")
public class ScanJobEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private UserEntity user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "printer_id")
    private PrinterEntity printer;

    @Column(nullable = false, length = 64)
    private String username;

    @Column(name = "printer_name", length = 128)
    private String printerName;

    @Column(name = "printer_ip", length = 64)
    private String printerIp;

    @Column(name = "recipient_email", nullable = false, length = 255)
    private String recipientEmail;

    @Column(length = 255)
    private String subject;

    @Column(name = "file_name", length = 255)
    private String fileName;

    @Column(nullable = false)
    private int pages = 1;

    @Column(name = "color_mode", length = 32)
    private String colorMode = "COLOR";

    @Column(length = 32)
    private String resolution = "300DPI";

    @Column(name = "file_format", length = 32)
    private String fileFormat = "PDF";

    @Column(length = 32)
    private String duplex = "SIMPLEX";

    @Column(nullable = false, length = 32)
    private String status = "SENT";

    @Column(name = "delivery_message", length = 512)
    private String deliveryMessage;

    @Column(name = "completed_at", nullable = false)
    private Instant completedAt = Instant.now();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public ScanJobEntity() {
    }

    public ScanJobEntity(UserEntity user, PrinterEntity printer, String username, String printerName, String printerIp,
                         String recipientEmail, String subject, String fileName, int pages, String colorMode,
                         String resolution, String fileFormat, String duplex, String status, String deliveryMessage) {
        this.user = user;
        this.printer = printer;
        this.username = username;
        this.printerName = printerName;
        this.printerIp = printerIp;
        this.recipientEmail = recipientEmail;
        this.subject = subject;
        this.fileName = fileName;
        this.pages = pages;
        this.colorMode = colorMode;
        this.resolution = resolution;
        this.fileFormat = fileFormat;
        this.duplex = duplex;
        this.status = status;
        this.deliveryMessage = deliveryMessage;
        this.completedAt = Instant.now();
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public UserEntity getUser() { return user; }
    public void setUser(UserEntity user) { this.user = user; }

    public PrinterEntity getPrinter() { return printer; }
    public void setPrinter(PrinterEntity printer) { this.printer = printer; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPrinterName() { return printerName; }
    public void setPrinterName(String printerName) { this.printerName = printerName; }

    public String getPrinterIp() { return printerIp; }
    public void setPrinterIp(String printerIp) { this.printerIp = printerIp; }

    public String getRecipientEmail() { return recipientEmail; }
    public void setRecipientEmail(String recipientEmail) { this.recipientEmail = recipientEmail; }

    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public int getPages() { return pages; }
    public void setPages(int pages) { this.pages = pages; }

    public String getColorMode() { return colorMode; }
    public void setColorMode(String colorMode) { this.colorMode = colorMode; }

    public String getResolution() { return resolution; }
    public void setResolution(String resolution) { this.resolution = resolution; }

    public String getFileFormat() { return fileFormat; }
    public void setFileFormat(String fileFormat) { this.fileFormat = fileFormat; }

    public String getDuplex() { return duplex; }
    public void setDuplex(String duplex) { this.duplex = duplex; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getDeliveryMessage() { return deliveryMessage; }
    public void setDeliveryMessage(String deliveryMessage) { this.deliveryMessage = deliveryMessage; }

    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
