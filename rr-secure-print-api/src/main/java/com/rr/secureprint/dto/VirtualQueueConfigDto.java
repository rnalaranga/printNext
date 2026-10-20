package com.rr.secureprint.dto;

import com.rr.secureprint.entity.PrinterEntity;
import java.util.List;

public record VirtualQueueConfigDto(
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
) {}
