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
        String message,
        boolean unregisteredCard,
        String badgeId
) {
    public AuthResponse(boolean authenticated, String username, String displayName, String userSid,
                        String department, String role, long printCount, long copyCount, String message) {
        this(authenticated, username, displayName, userSid, department, role, printCount, copyCount, message, false, null);
    }

    public static AuthResponse success(String username, String displayName, String userSid, String department, String role, long printCount, long copyCount) {
        return new AuthResponse(true, username, displayName, userSid, department, role, printCount, copyCount, "Authentication successful", false, null);
    }

    public static AuthResponse success(String username, String displayName, String userSid, String department, String role) {
        return success(username, displayName, userSid, department, role, 0L, 0L);
    }

    public static AuthResponse failure(String message) {
        return new AuthResponse(false, null, null, null, null, null, 0L, 0L, message, false, null);
    }

    public static AuthResponse unregisteredCard(String badgeId, String message) {
        return new AuthResponse(false, null, null, null, null, null, 0L, 0L, message, true, badgeId);
    }
}
