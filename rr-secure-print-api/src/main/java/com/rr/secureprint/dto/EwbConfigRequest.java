package com.rr.secureprint.dto;

public record EwbConfigRequest(
        String printerIp,
        String adminUsername,
        String adminPassword,
        String terminalUrl,
        Boolean lockHomeFunctions
) {
    public EwbConfigRequest(String printerIp, String adminUsername, String adminPassword, String terminalUrl) {
        this(printerIp, adminUsername, adminPassword, terminalUrl, true);
    }
}
