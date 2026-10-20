package com.rr.secureprint.dto;

import com.rr.secureprint.entity.PrinterEntity;
import java.util.List;

public record VirtualQueueConfigDto(
        Long id,
        String queueCode,
        String name,
        String selectedPrinterName,
        String shareName,
        String driverName,
        String description,
        boolean isDefault,
        boolean isShared,
        boolean isPaused,
        int jobCount,
        String serverHost,
        String networkSharePath,
        List<ServerPrinterDto> availableServerPrinters,
        List<PrinterEntity> physicalPrinters,
        int physicalPrinterCount,
        boolean livePrintingEnabled,
        List<VirtualQueueSummaryDto> allQueues
) {
    // Backward-compatible constructor
    public VirtualQueueConfigDto(
            String selectedPrinterName,
            String shareName,
            String driverName,
            boolean isShared,
            boolean isPaused,
            int jobCount,
            String serverHost,
            String networkSharePath,
            List<ServerPrinterDto> availableServerPrinters,
            List<PrinterEntity> physicalPrinters,
            boolean livePrintingEnabled
    ) {
        this(
                1L,
                "VQ-DEFAULT",
                "General Find-Me Virtual Queue",
                selectedPrinterName,
                shareName,
                driverName,
                "Default Virtual Print Queue",
                true,
                isShared,
                isPaused,
                jobCount,
                serverHost,
                networkSharePath,
                availableServerPrinters,
                physicalPrinters,
                physicalPrinters != null ? physicalPrinters.size() : 0,
                livePrintingEnabled,
                List.of()
        );
    }
}
