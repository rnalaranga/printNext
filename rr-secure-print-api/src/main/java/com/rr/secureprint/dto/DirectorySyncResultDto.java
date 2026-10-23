package com.rr.secureprint.dto;

import java.util.List;

public record DirectorySyncResultDto(
        boolean success,
        String message,
        int totalFound,
        int importedCount,
        int updatedCount,
        List<String> userSummary
) {}
