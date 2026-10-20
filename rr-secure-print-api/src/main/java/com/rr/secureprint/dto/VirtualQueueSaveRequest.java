package com.rr.secureprint.dto;

import java.util.List;

public record VirtualQueueSaveRequest(
        String name,
        String queueCode,
        String windowsPrinterName,
        String shareName,
        String driverName,
        String description,
        Boolean isDefault,
        List<Long> physicalPrinterIds
) {}
