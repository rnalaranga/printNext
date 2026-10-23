package com.rr.secureprint.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "copy_jobs")
public class CopyJobEntity {

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

    @Column(nullable = false)
    private int pages = 1;

    @Column(name = "color_pages", nullable = false)
    private int colorPages = 0;

    @Column(name = "mono_pages", nullable = false)
    private int monoPages = 0;

    @Column(name = "printer_job_id", length = 64)
    private String printerJobId;

    @Column(name = "completed_at", nullable = false)
    private Instant completedAt = Instant.now();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public CopyJobEntity() {
    }

    public CopyJobEntity(UserEntity user, PrinterEntity printer, String username, String printerName, String printerIp,
                         int pages, int colorPages, int monoPages, String printerJobId, Instant completedAt) {
        this.user = user;
        this.printer = printer;
        this.username = username;
        this.printerName = printerName;
        this.printerIp = printerIp;
        this.pages = pages;
        this.colorPages = colorPages;
        this.monoPages = monoPages;
        this.printerJobId = printerJobId;
        this.completedAt = completedAt != null ? completedAt : Instant.now();
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

    public int getPages() { return pages; }
    public void setPages(int pages) { this.pages = pages; }

    public int getColorPages() { return colorPages; }
    public void setColorPages(int colorPages) { this.colorPages = colorPages; }

    public int getMonoPages() { return monoPages; }
    public void setMonoPages(int monoPages) { this.monoPages = monoPages; }

    public String getPrinterJobId() { return printerJobId; }
    public void setPrinterJobId(String printerJobId) { this.printerJobId = printerJobId; }

    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
