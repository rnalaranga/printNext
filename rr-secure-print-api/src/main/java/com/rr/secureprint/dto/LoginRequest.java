package com.rr.secureprint.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        String username,
        String password,
        String pin,
        String badgeId
) {
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
