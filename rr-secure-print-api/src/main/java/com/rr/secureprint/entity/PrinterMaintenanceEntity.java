package com.rr.secureprint.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "printer_maintenance_logs")
public class PrinterMaintenanceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "printer_id")
    private Long printerId;

    @Column(name = "printer_code", length = 64)
    private String printerCode;

    @Column(name = "printer_name", length = 160)
    private String printerName;

    @Column(name = "printer_ip", length = 64)
    private String printerIp;

    @Column(name = "maintenance_type", nullable = false, length = 40)
    private String maintenanceType;

    @Column(name = "toner_color", length = 32)
    private String tonerColor = "N/A";

    @Column(name = "part_replaced", length = 200)
    private String partReplaced;

    @Column(name = "part_number", length = 80)
    private String partNumber;

    @Column(name = "part_serial", length = 80)
    private String partSerial;

    @Column(name = "odometer_pages")
    private Long odometerPages = 0L;

    @Column(name = "cost_lkr")
    private Double costLkr = 0.0;

    @Column(name = "technician", length = 120)
    private String technician;

    @Column(name = "vendor", length = 120)
    private String vendor;

    @Column(name = "status", nullable = false, length = 32)
    private String status = "COMPLETED";

    @Column(name = "service_date", nullable = false, length = 20)
    private String serviceDate;

    @Column(name = "next_service_date", length = 20)
    private String nextServiceDate;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at")
    private Instant createdAt = Instant.now();

    @Column(name = "started_at")
    private Instant startedAt = Instant.now();

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "duration_minutes")
    private Long durationMinutes = 0L;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getPrinterId() { return printerId; }
    public void setPrinterId(Long printerId) { this.printerId = printerId; }
    public String getPrinterCode() { return printerCode; }
    public void setPrinterCode(String printerCode) { this.printerCode = printerCode; }
    public String getPrinterName() { return printerName; }
    public void setPrinterName(String printerName) { this.printerName = printerName; }
    public String getPrinterIp() { return printerIp; }
    public void setPrinterIp(String printerIp) { this.printerIp = printerIp; }
    public String getMaintenanceType() { return maintenanceType; }
    public void setMaintenanceType(String maintenanceType) { this.maintenanceType = maintenanceType; }
    public String getTonerColor() { return tonerColor; }
    public void setTonerColor(String tonerColor) { this.tonerColor = tonerColor; }
    public String getPartReplaced() { return partReplaced; }
    public void setPartReplaced(String partReplaced) { this.partReplaced = partReplaced; }
    public String getPartNumber() { return partNumber; }
    public void setPartNumber(String partNumber) { this.partNumber = partNumber; }
    public String getPartSerial() { return partSerial; }
    public void setPartSerial(String partSerial) { this.partSerial = partSerial; }
    public Long getOdometerPages() { return odometerPages; }
    public void setOdometerPages(Long odometerPages) { this.odometerPages = odometerPages; }
    public Double getCostLkr() { return costLkr; }
    public void setCostLkr(Double costLkr) { this.costLkr = costLkr; }
    public String getTechnician() { return technician; }
    public void setTechnician(String technician) { this.technician = technician; }
    public String getVendor() { return vendor; }
    public void setVendor(String vendor) { this.vendor = vendor; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getServiceDate() { return serviceDate; }
    public void setServiceDate(String serviceDate) { this.serviceDate = serviceDate; }
    public String getNextServiceDate() { return nextServiceDate; }
    public void setNextServiceDate(String nextServiceDate) { this.nextServiceDate = nextServiceDate; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public Long getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(Long durationMinutes) { this.durationMinutes = durationMinutes; }
}
