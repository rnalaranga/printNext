package com.rr.secureprint.dto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class FleetMonitoringSummaryDto implements Serializable {

    private int totalPrinters = 0;
    private int onlinePrinters = 0;
    private int printingPrinters = 0;
    private int warningPrinters = 0;
    private int errorPrinters = 0;
    private int offlinePrinters = 0;
    private long totalFleetPrints = 0L;
    private double averageTonerPercent = 0.0;
    private String lastUpdated;
    private List<PrinterMonitoringDto> printers = new ArrayList<>();

    public FleetMonitoringSummaryDto() {
    }

    public int getTotalPrinters() { return totalPrinters; }
    public void setTotalPrinters(int totalPrinters) { this.totalPrinters = totalPrinters; }

    public int getOnlinePrinters() { return onlinePrinters; }
    public void setOnlinePrinters(int onlinePrinters) { this.onlinePrinters = onlinePrinters; }

    public int getPrintingPrinters() { return printingPrinters; }
    public void setPrintingPrinters(int printingPrinters) { this.printingPrinters = printingPrinters; }

    public int getWarningPrinters() { return warningPrinters; }
    public void setWarningPrinters(int warningPrinters) { this.warningPrinters = warningPrinters; }

    public int getErrorPrinters() { return errorPrinters; }
    public void setErrorPrinters(int errorPrinters) { this.errorPrinters = errorPrinters; }

    public int getOfflinePrinters() { return offlinePrinters; }
    public void setOfflinePrinters(int offlinePrinters) { this.offlinePrinters = offlinePrinters; }

    public long getTotalFleetPrints() { return totalFleetPrints; }
    public void setTotalFleetPrints(long totalFleetPrints) { this.totalFleetPrints = totalFleetPrints; }

    public double getAverageTonerPercent() { return averageTonerPercent; }
    public void setAverageTonerPercent(double averageTonerPercent) { this.averageTonerPercent = averageTonerPercent; }

    public String getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(String lastUpdated) { this.lastUpdated = lastUpdated; }

    public List<PrinterMonitoringDto> getPrinters() { return printers; }
    public void setPrinters(List<PrinterMonitoringDto> printers) { this.printers = printers; }
}
