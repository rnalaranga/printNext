package com.rr.secureprint.dto;

import com.rr.secureprint.entity.DirectoryConfigEntity;
import java.time.Instant;

public record DirectoryConfigDto(
        Long id,
        String directoryType,
        String ldapUrl,
        String domainName,
        String baseDn,
        String bindUser,
        String bindPassword,
        String userSearchFilter,
        String defaultPin,
        boolean isEnabled,
        Instant lastSyncAt,
        String lastSyncStatus,
        String lastSyncMessage,
        int syncedUserCount,
        String cardIdAttribute,
        String secondaryCardAttribute,
        String externalAttr1Name,
        String externalAttr2Name,
        String externalAttr3Name,
        String usernameAttribute,
        String displayNameAttribute,
        String emailAttribute,
        String departmentAttribute
) {
    public static DirectoryConfigDto fromEntity(DirectoryConfigEntity e, boolean maskPassword) {
        String pass = e.getBindPassword();
        if (maskPassword && pass != null && !pass.isBlank()) {
            pass = "********";
        }
        return new DirectoryConfigDto(
                e.getId(),
                e.getDirectoryType(),
                e.getLdapUrl(),
                e.getDomainName(),
                e.getBaseDn(),
                e.getBindUser(),
                pass,
                e.getUserSearchFilter(),
                e.getDefaultPin(),
                e.isEnabled(),
                e.getLastSyncAt(),
                e.getLastSyncStatus(),
                e.getLastSyncMessage(),
                e.getSyncedUserCount(),
                e.getCardIdAttribute(),
                e.getSecondaryCardAttribute(),
                e.getExternalAttr1Name(),
                e.getExternalAttr2Name(),
                e.getExternalAttr3Name(),
                e.getUsernameAttribute(),
                e.getDisplayNameAttribute(),
                e.getEmailAttribute(),
                e.getDepartmentAttribute()
        );
    }
}
