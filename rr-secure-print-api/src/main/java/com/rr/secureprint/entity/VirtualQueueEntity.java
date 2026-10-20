package com.rr.secureprint.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "virtual_queues")
public class VirtualQueueEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "queue_code", nullable = false, unique = true, length = 64)
    private String queueCode;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(name = "windows_printer_name", nullable = false, length = 128)
    private String windowsPrinterName;

    @Column(name = "share_name", length = 128)
    private String shareName;

    @Column(name = "driver_name", length = 128)
    private String driverName = "TOSHIBA Universal Printer 2";

    @Column(length = 255)
    private String description;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault = false;

    @Column(name = "created_at")
    private Instant createdAt = Instant.now();

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "virtual_queue_printers",
            joinColumns = @JoinColumn(name = "virtual_queue_id"),
            inverseJoinColumns = @JoinColumn(name = "printer_id")
    )
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private List<PrinterEntity> physicalPrinters = new ArrayList<>();

    public VirtualQueueEntity() {}

    public VirtualQueueEntity(String queueCode, String name, String windowsPrinterName, String shareName, String driverName, String description, boolean isDefault) {
        this.queueCode = queueCode;
        this.name = name;
        this.windowsPrinterName = windowsPrinterName;
        this.shareName = (shareName != null && !shareName.isBlank()) ? shareName : windowsPrinterName;
        this.driverName = (driverName != null && !driverName.isBlank()) ? driverName : "TOSHIBA Universal Printer 2";
        this.description = description;
        this.isActive = true;
        this.isDefault = isDefault;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getQueueCode() { return queueCode; }
    public void setQueueCode(String queueCode) { this.queueCode = queueCode; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getWindowsPrinterName() { return windowsPrinterName; }
    public void setWindowsPrinterName(String windowsPrinterName) { this.windowsPrinterName = windowsPrinterName; }

    public String getShareName() { return shareName != null ? shareName : windowsPrinterName; }
    public void setShareName(String shareName) { this.shareName = shareName; }

    public String getDriverName() { return driverName != null ? driverName : "TOSHIBA Universal Printer 2"; }
    public void setDriverName(String driverName) { this.driverName = driverName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }

    public boolean isDefault() { return isDefault; }
    public void setDefault(boolean aDefault) { isDefault = aDefault; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public List<PrinterEntity> getPhysicalPrinters() { return physicalPrinters; }
    public void setPhysicalPrinters(List<PrinterEntity> physicalPrinters) { this.physicalPrinters = physicalPrinters; }
}
