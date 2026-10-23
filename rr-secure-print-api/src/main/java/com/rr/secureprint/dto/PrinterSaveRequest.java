package com.rr.secureprint.dto;

public record PrinterSaveRequest(
        String printerCode,
        String name,
        String ipAddress,
        Integer rawPort,
        String location,
        String model,
        Boolean colorSupported,
        Boolean duplexSupported,
        Boolean isActive,
        String adminUsername,
        String adminPassword,
        Boolean autoConfigureEwb,
        String terminalUrl,
        Boolean isDefaultHoldQueue,
        String deviceGeneration
) {
    public PrinterSaveRequest(
            String printerCode,
            String name,
            String ipAddress,
            Integer rawPort,
            String location,
            String model,
            Boolean colorSupported,
            Boolean duplexSupported,
            Boolean isActive,
            String adminUsername,
            String adminPassword,
            Boolean autoConfigureEwb,
            String terminalUrl,
            Boolean isDefaultHoldQueue
    ) {
        this(printerCode, name, ipAddress, rawPort, location, model, colorSupported, duplexSupported, isActive, adminUsername, adminPassword, autoConfigureEwb, terminalUrl, isDefaultHoldQueue, "TOSHIBA_NEW");
    }
}
