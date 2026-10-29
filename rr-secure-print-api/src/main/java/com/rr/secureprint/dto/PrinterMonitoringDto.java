package com.rr.secureprint.dto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class PrinterMonitoringDto implements Serializable {

    private Long id;
    private String printerCode;
    private String name;
    private String ipAddress;
    private String location;
    private String department;
    private String floor;
    private String model;
    private String status; // "READY", "PRINTING", "WARNING", "ERROR", "OFFLINE"
    private String statusMessage;
    private String uptime;
    private Long lifeCount = 0L;
    private boolean isRealHardware = false;
    private boolean isColor = false;
    private Long pingMs = 0L;
    private String lastPolled;

    // Advanced Engine & Error Telemetry
    private boolean currentlyPrinting = false;
    private String hrPrinterStatusText = "IDLE"; // "IDLE", "PRINTING", "WARMUP", "DOWN", "OFFLINE"
    private String wasteTonerStatus = "OK";      // "OK", "NEAR_FULL", "FULL"
    private List<String> detectedErrors = new ArrayList<>();
    private List<AlertDetailItem> alertDetails = new ArrayList<>();

    private List<TonerItem> toners = new ArrayList<>();
    private List<TrayItem> trays = new ArrayList<>();
    private List<String> alerts = new ArrayList<>();

    public PrinterMonitoringDto() {
    }

    public static class AlertDetailItem implements Serializable {
        private int code;
        private String codeName;
        private String description;
        private String severity; // "CRITICAL", "WARNING", "INFO"
        private String location;
        private String time;

        public AlertDetailItem() {}

        public AlertDetailItem(int code, String codeName, String description, String severity, String location, String time) {
            this.code = code;
            this.codeName = codeName;
            this.description = description;
            this.severity = severity;
            this.location = location;
            this.time = time;
        }

        public int getCode() { return code; }
        public void setCode(int code) { this.code = code; }
        public String getCodeName() { return codeName; }
        public void setCodeName(String codeName) { this.codeName = codeName; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getSeverity() { return severity; }
        public void setSeverity(String severity) { this.severity = severity; }
        public String getLocation() { return location; }
        public void setLocation(String location) { this.location = location; }
        public String getTime() { return time; }
        public void setTime(String time) { this.time = time; }
    }

    public static class TonerItem implements Serializable {
        private String name;          // e.g. "Black", "Cyan", "Magenta", "Yellow"
        private String color;         // Hex code e.g. "#111827", "#06b6d4", "#ec4899", "#eab308"
        private int levelPercent;     // 0 - 100 (-1 if unknown)
        private String status;        // "OK", "LOW", "EMPTY"

        public TonerItem() {}

        public TonerItem(String name, String color, int levelPercent, String status) {
            this.name = name;
            this.color = color;
            this.levelPercent = levelPercent;
            this.status = status;
        }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getColor() { return color; }
        public void setColor(String color) { this.color = color; }
        public int getLevelPercent() { return levelPercent; }
        public void setLevelPercent(int levelPercent) { this.levelPercent = levelPercent; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }

    public static class TrayItem implements Serializable {
        private int trayIndex;
        private String trayName;      // e.g. "Drawer 1", "Drawer 2", "Large Capacity Feeder", "Bypass"
        private String paperSize;     // "A4", "A3", "Letter", etc.
        private int levelPercent;     // 0 - 100
        private String status;        // "OK", "LOW", "EMPTY"

        public TrayItem() {}

        public TrayItem(int trayIndex, String trayName, String paperSize, int levelPercent, String status) {
            this.trayIndex = trayIndex;
            this.trayName = trayName;
            this.paperSize = paperSize;
            this.levelPercent = levelPercent;
            this.status = status;
        }

        public int getTrayIndex() { return trayIndex; }
        public void setTrayIndex(int trayIndex) { this.trayIndex = trayIndex; }
        public String getTrayName() { return trayName; }
        public void setTrayName(String trayName) { this.trayName = trayName; }
        public String getPaperSize() { return paperSize; }
        public void setPaperSize(String paperSize) { this.paperSize = paperSize; }
        public int getLevelPercent() { return levelPercent; }
        public void setLevelPercent(int levelPercent) { this.levelPercent = levelPercent; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getPrinterCode() { return printerCode; }
    public void setPrinterCode(String printerCode) { this.printerCode = printerCode; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }
    public String getFloor() { return floor; }
    public void setFloor(String floor) { this.floor = floor; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getStatusMessage() { return statusMessage; }
    public void setStatusMessage(String statusMessage) { this.statusMessage = statusMessage; }
    public String getUptime() { return uptime; }
    public void setUptime(String uptime) { this.uptime = uptime; }
    public Long getLifeCount() { return lifeCount; }
    public void setLifeCount(Long lifeCount) { this.lifeCount = lifeCount; }
    public boolean isRealHardware() { return isRealHardware; }
    public void setRealHardware(boolean realHardware) { isRealHardware = realHardware; }
    public boolean isColor() { return isColor; }
    public void setColor(boolean isColor) { this.isColor = isColor; }
    public Long getPingMs() { return pingMs; }
    public void setPingMs(Long pingMs) { this.pingMs = pingMs; }
    public String getLastPolled() { return lastPolled; }
    public void setLastPolled(String lastPolled) { this.lastPolled = lastPolled; }

    public boolean isCurrentlyPrinting() { return currentlyPrinting; }
    public void setCurrentlyPrinting(boolean currentlyPrinting) { this.currentlyPrinting = currentlyPrinting; }
    public String getHrPrinterStatusText() { return hrPrinterStatusText; }
    public void setHrPrinterStatusText(String hrPrinterStatusText) { this.hrPrinterStatusText = hrPrinterStatusText; }
    public String getWasteTonerStatus() { return wasteTonerStatus; }
    public void setWasteTonerStatus(String wasteTonerStatus) { this.wasteTonerStatus = wasteTonerStatus; }
    public List<String> getDetectedErrors() { return detectedErrors; }
    public void setDetectedErrors(List<String> detectedErrors) { this.detectedErrors = detectedErrors; }
    public List<AlertDetailItem> getAlertDetails() { return alertDetails; }
    public void setAlertDetails(List<AlertDetailItem> alertDetails) { this.alertDetails = alertDetails; }

    public List<TonerItem> getToners() { return toners; }
    public void setToners(List<TonerItem> toners) { this.toners = toners; }
    public List<TrayItem> getTrays() { return trays; }
    public void setTrays(List<TrayItem> trays) { this.trays = trays; }
    public List<String> getAlerts() { return alerts; }
    public void setAlerts(List<String> alerts) { this.alerts = alerts; }
}
