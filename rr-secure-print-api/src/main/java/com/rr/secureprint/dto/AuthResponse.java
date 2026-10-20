package com.rr.secureprint.dto;

public record AuthResponse(
        boolean authenticated,
        String username,
        String displayName,
        String userSid,
        String department,
        String role,
        String message
) {
    public static AuthResponse success(String username, String displayName, String userSid, String department, String role) {
        return new AuthResponse(true, username, displayName, userSid, department, role, "Authentication successful");
    }

    public static AuthResponse failure(String message) {
        return new AuthResponse(false, null, null, null, null, null, message);
    }
}
