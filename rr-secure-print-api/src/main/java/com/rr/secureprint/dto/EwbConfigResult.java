package com.rr.secureprint.dto;

import java.time.Instant;

public record EwbConfigResult(
        boolean success,
        String message,
        String printerModel,
        String configuredUrl,
        boolean autoScreenChange,
        Instant timestamp
) {}
