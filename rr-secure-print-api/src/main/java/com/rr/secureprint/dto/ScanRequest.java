package com.rr.secureprint.dto;

public record ScanRequest(
        String printerCode,
        String printerIp,
        String recipientEmail,
        String subject,
        String fileName,
        Integer pages,
        String colorMode,
        String resolution,
        String fileFormat,
        String duplex
) {}
