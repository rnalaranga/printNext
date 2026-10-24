package com.rr.secureprint.dto;

public record UserSaveRequest(
        String username,
        String displayName,
        String userSid,
        String pin,
        String password,
        String badgeId,
        String secondaryBadgeId,
        String email,
        String department,
        String role,
        String authSource,
        Boolean isActive
) {
    public UserSaveRequest(String username, String displayName, String userSid, String pin, String password, String badgeId, String email, String department, String role, String authSource, Boolean isActive) {
        this(username, displayName, userSid, pin, password, badgeId, null, email, department, role, authSource, isActive);
    }

    public UserSaveRequest(String username, String displayName, String userSid, String pin, String badgeId, String email, String department, String role, String authSource, Boolean isActive) {
        this(username, displayName, userSid, pin, null, badgeId, null, email, department, role, authSource, isActive);
    }

    public UserSaveRequest(String username, String displayName, String userSid, String pin, String badgeId, String email, String department, String role, Boolean isActive) {
        this(username, displayName, userSid, pin, null, badgeId, null, email, department, role, "MANUAL", isActive);
    }
}
