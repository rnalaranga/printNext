package com.rr.secureprint.dto;

public record UserSaveRequest(
        String username,
        String displayName,
        String userSid,
        String pin,
        String badgeId,
        String email,
        String department,
        String role,
        Boolean isActive
) {}
