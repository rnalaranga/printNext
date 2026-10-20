package com.rr.secureprint.dto;

public record PrinterTestResult(
        boolean success,
        long responseTimeMs,
        String message
) {}
