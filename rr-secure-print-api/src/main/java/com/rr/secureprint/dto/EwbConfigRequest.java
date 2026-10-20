package com.rr.secureprint.dto;

public record EwbConfigRequest(
        String printerIp,
        String adminUsername,
        String adminPassword,
        String terminalUrl
) {}
