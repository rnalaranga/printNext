package com.rr.secureprint.dto;

import com.rr.secureprint.entity.UserEntity;
import java.time.Instant;

public record UserDto(
        Long id,
        String username,
        String displayName,
        String userSid,
        String badgeId,
        String secondaryBadgeId,
        String email,
        String department,
        String role,
        String authSource,
        boolean isActive,
        boolean hasPin,
        boolean hasPassword,
        long printCount,
        long copyCount,
        long totalPages,
        Integer pageQuota,
        int heldJobsCount,
        Instant createdAt,
        String externalAttribute1,
        String externalAttribute2,
        String externalAttribute3
) {
    public String getCostCenter() {
        return externalAttribute3 != null && !externalAttribute3.isBlank() ? externalAttribute3 : "Unassigned";
    }

    public static UserDto fromEntity(UserEntity user) {
        return fromEntity(user, 0);
    }

    public static UserDto fromEntity(UserEntity user, int heldJobsCount) {
        return new UserDto(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getUserSid(),
                user.getBadgeId(),
                user.getSecondaryBadgeId(),
                user.getEmail(),
                user.getDepartment(),
                user.getRole(),
                user.getAuthSource(),
                user.isActive(),
                user.getPinHash() != null && !user.getPinHash().isBlank(),
                user.getPasswordHash() != null && !user.getPasswordHash().isBlank(),
                user.getPrintCount(),
                user.getCopyCount(),
                user.getTotalPages(),
                user.getPageQuota(),
                heldJobsCount,
                user.getCreatedAt(),
                user.getExternalAttribute1(),
                user.getExternalAttribute2(),
                user.getExternalAttribute3()
        );
    }
}
