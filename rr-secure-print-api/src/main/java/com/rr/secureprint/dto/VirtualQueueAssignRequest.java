package com.rr.secureprint.dto;

import java.util.List;

public record VirtualQueueAssignRequest(
        List<Long> physicalPrinterIds
) {}
