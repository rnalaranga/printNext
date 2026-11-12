package com.rr.secureprint.dto;

public record ReleaseRequest(
        String printerCode,
        String drawer,
        String paperSize
) {
    public ReleaseRequest(String printerCode) {
        this(printerCode, "AUTO", "AUTO");
    }
}
