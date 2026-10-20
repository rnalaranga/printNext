package com.rr.secureprint.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "printers")
public class PrinterEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "printer_code", nullable = false, unique = true, length = 64)
    private String printerCode;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(name = "ip_address", nullable = false, length = 64)
    private String ipAddress;

    @Column(name = "raw_port", nullable = false)
    private int rawPort = 9100;

    @Column(nullable = false, length = 128)
    private String location;

    @Column(length = 64)
    private String model = "Toshiba e-STUDIO 3518A";

    @Column(name = "color_supported", nullable = false)
    private boolean colorSupported = false;

    @Column(name = "duplex_supported", nullable = false)
    private boolean duplexSupported = true;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @Column(nullable = false, length = 32)
    private String status = "ONLINE";

    @Column(name = "admin_username", length = 64)
    private String adminUsername = "admin";

    @Column(name = "admin_password", length = 128)
    private String adminPassword = "123456";

    @Column(name = "ewb_configured")
    private boolean ewbConfigured = false;

    @Column(name = "ewb_url", length = 255)
    private String ewbUrl;

    @Column(name = "ewb_configured_at")
    private Instant ewbConfiguredAt;

    @Column(name = "is_default_hold_queue", nullable = false)
    private boolean isDefaultHoldQueue = false;

    @Column(name = "created_at")
    private Instant createdAt = Instant.now();

    public PrinterEntity() {}

    public PrinterEntity(String printerCode, String name, String ipAddress, int rawPort, String location, String model, boolean colorSupported, boolean duplexSupported) {
        this.printerCode = printerCode;
        this.name = name;
        this.ipAddress = ipAddress;
        this.rawPort = rawPort;
        this.location = location;
        this.model = model != null ? model : "Toshiba e-STUDIO 3518A";
        this.colorSupported = colorSupported;
        this.duplexSupported = duplexSupported;
        this.isActive = true;
        this.status = "ONLINE";
        this.adminUsername = "admin";
        this.adminPassword = "123456";
        this.ewbConfigured = false;
        this.isDefaultHoldQueue = false;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getPrinterCode() { return printerCode; }
    public void setPrinterCode(String printerCode) { this.printerCode = printerCode; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }

    public int getRawPort() { return rawPort; }
    public void setRawPort(int rawPort) { this.rawPort = rawPort; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public boolean isColorSupported() { return colorSupported; }
    public void setColorSupported(boolean colorSupported) { this.colorSupported = colorSupported; }

    public boolean isDuplexSupported() { return duplexSupported; }
    public void setDuplexSupported(boolean duplexSupported) { this.duplexSupported = duplexSupported; }

    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getAdminUsername() { return adminUsername; }
    public void setAdminUsername(String adminUsername) { this.adminUsername = adminUsername; }

    public String getAdminPassword() { return adminPassword; }
    public void setAdminPassword(String adminPassword) { this.adminPassword = adminPassword; }

    public boolean isEwbConfigured() { return ewbConfigured; }
    public void setEwbConfigured(boolean ewbConfigured) { this.ewbConfigured = ewbConfigured; }

    public String getEwbUrl() { return ewbUrl; }
    public void setEwbUrl(String ewbUrl) { this.ewbUrl = ewbUrl; }

    public Instant getEwbConfiguredAt() { return ewbConfiguredAt; }
    public void setEwbConfiguredAt(Instant ewbConfiguredAt) { this.ewbConfiguredAt = ewbConfiguredAt; }

    public boolean isDefaultHoldQueue() { return isDefaultHoldQueue; }
    public void setDefaultHoldQueue(boolean defaultHoldQueue) { isDefaultHoldQueue = defaultHoldQueue; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
