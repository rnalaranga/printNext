package com.rr.secureprint.dto;

public record UserSaveRequest(
        String username,
        String displayName,
        String userSid,
        String pin,
        String password,
        String badgeId,
        String email,
        String department,
        String role,
        String authSource,
        Boolean isActive
) {
    public UserSaveRequest(String username, String displayName, String userSid, String pin, String badgeId, String email, String department, String role, String authSource, Boolean isActive) {
        this(username, displayName, userSid, pin, null, badgeId, email, department, role, authSource, isActive);
    }

    public UserSaveRequest(String username, String displayName, String userSid, String pin, String badgeId, String email, String department, String role, Boolean isActive) {
        this(username, displayName, userSid, pin, null, badgeId, email, department, role, "MANUAL", isActive);
    }
}
