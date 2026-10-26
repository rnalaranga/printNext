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
        Boolean isActive,
        String externalAttribute1,
        String externalAttribute2,
        String externalAttribute3,
        String costCenter
) {
    public UserSaveRequest {
        if ((externalAttribute3 == null || externalAttribute3.isBlank()) && costCenter != null && !costCenter.isBlank()) {
            externalAttribute3 = costCenter.trim();
        }
    }

    public UserSaveRequest(String username, String displayName, String userSid, String pin, String password, String badgeId, String secondaryBadgeId, String email, String department, String role, String authSource, Boolean isActive, String externalAttribute1, String externalAttribute2, String externalAttribute3) {
        this(username, displayName, userSid, pin, password, badgeId, secondaryBadgeId, email, department, role, authSource, isActive, externalAttribute1, externalAttribute2, externalAttribute3, externalAttribute3);
    }

    public UserSaveRequest(String username, String displayName, String userSid, String pin, String password, String badgeId, String secondaryBadgeId, String email, String department, String role, String authSource, Boolean isActive) {
        this(username, displayName, userSid, pin, password, badgeId, secondaryBadgeId, email, department, role, authSource, isActive, null, null, null, null);
    }

    public UserSaveRequest(String username, String displayName, String userSid, String pin, String password, String badgeId, String email, String department, String role, String authSource, Boolean isActive) {
        this(username, displayName, userSid, pin, password, badgeId, null, email, department, role, authSource, isActive, null, null, null, null);
    }

    public UserSaveRequest(String username, String displayName, String userSid, String pin, String badgeId, String email, String department, String role, String authSource, Boolean isActive) {
        this(username, displayName, userSid, pin, null, badgeId, null, email, department, role, authSource, isActive, null, null, null, null);
    }

    public UserSaveRequest(String username, String displayName, String userSid, String pin, String badgeId, String email, String department, String role, Boolean isActive) {
        this(username, displayName, userSid, pin, null, badgeId, null, email, department, role, "MANUAL", isActive, null, null, null, null);
    }
}
