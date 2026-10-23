package com.rr.secureprint.dto;

public record LoginRequest(
        String username,
        String password,
        String pin,
        String badgeId,
        String printerIp,
        String printerCode
) {
    public LoginRequest(String username, String password, String pin, String badgeId) {
        this(username, password, pin, badgeId, null, null);
    }

    public boolean hasPin() {
        return pin != null && !pin.isBlank();
    }

    public boolean hasBadge() {
        return badgeId != null && !badgeId.isBlank();
    }

    public boolean hasCredentials() {
        return username != null && !username.isBlank() && password != null && !password.isBlank();
    }
}
