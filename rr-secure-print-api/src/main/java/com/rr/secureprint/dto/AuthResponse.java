package com.rr.secureprint.dto;

public record AuthResponse(
        boolean authenticated,
        String username,
        String displayName,
        String userSid,
        String department,
        String role,
        long printCount,
        long copyCount,
        String message
) {
    public static AuthResponse success(String username, String displayName, String userSid, String department, String role, long printCount, long copyCount) {
        return new AuthResponse(true, username, displayName, userSid, department, role, printCount, copyCount, "Authentication successful");
    }

    public static AuthResponse success(String username, String displayName, String userSid, String department, String role) {
        return success(username, displayName, userSid, department, role, 0L, 0L);
    }

    public static AuthResponse failure(String message) {
        return new AuthResponse(false, null, null, null, null, null, 0L, 0L, message);
    }
}
