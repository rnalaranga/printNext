package com.rr.secureprint.dto;

import com.rr.secureprint.entity.UserEntity;
import java.time.Instant;

public record UserDto(
        Long id,
        String username,
        String displayName,
        String userSid,
        String badgeId,
        String email,
        String department,
        String role,
        boolean isActive,
        boolean hasPin,
        Instant createdAt
) {
    public static UserDto fromEntity(UserEntity user) {
        return new UserDto(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getUserSid(),
                user.getBadgeId(),
                user.getEmail(),
                user.getDepartment(),
                user.getRole(),
                user.isActive(),
                user.getPinHash() != null && !user.getPinHash().isBlank(),
                user.getCreatedAt()
        );
    }
}
