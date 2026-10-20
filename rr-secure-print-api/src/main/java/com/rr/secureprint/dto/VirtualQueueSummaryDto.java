package com.rr.secureprint.dto;

import java.time.Instant;

public record VirtualQueueSummaryDto(
        Long id,
        String queueCode,
        String name,
        String windowsPrinterName,
        String shareName,
        String driverName,
        String description,
        boolean isDefault,
        boolean isActive,
        int physicalPrinterCount,
        Instant createdAt
) {}
