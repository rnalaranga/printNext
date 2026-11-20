package com.rr.secureprint.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "printer_counter_reset_logs")
public class CounterResetLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "printer_code", nullable = false, length = 64)
    private String printerCode;

    @Column(name = "printer_name", length = 160)
    private String printerName;

    @Column(name = "department", length = 64)
    private String department;

    @Column(name = "previous_offset_pages")
    private Long previousOffsetPages = 0L;

    @Column(name = "actual_pages_at_reset")
    private Long actualPagesAtReset = 0L;

    @Column(name = "delta_since_last_reset")
    private Long deltaSinceLastReset = 0L;

    @Column(name = "reset_by", length = 64)
    private String resetBy = "admin";

    @Column(name = "reset_at", nullable = false)
    private Instant resetAt = Instant.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getPrinterCode() { return printerCode; }
    public void setPrinterCode(String printerCode) { this.printerCode = printerCode; }
    public String getPrinterName() { return printerName; }
    public void setPrinterName(String printerName) { this.printerName = printerName; }
    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }
    public Long getPreviousOffsetPages() { return previousOffsetPages; }
    public void setPreviousOffsetPages(Long previousOffsetPages) { this.previousOffsetPages = previousOffsetPages; }
    public Long getActualPagesAtReset() { return actualPagesAtReset; }
    public void setActualPagesAtReset(Long actualPagesAtReset) { this.actualPagesAtReset = actualPagesAtReset; }
    public Long getDeltaSinceLastReset() { return deltaSinceLastReset; }
    public void setDeltaSinceLastReset(Long deltaSinceLastReset) { this.deltaSinceLastReset = deltaSinceLastReset; }
    public String getResetBy() { return resetBy; }
    public void setResetBy(String resetBy) { this.resetBy = resetBy; }
    public Instant getResetAt() { return resetAt; }
    public void setResetAt(Instant resetAt) { this.resetAt = resetAt; }
}
