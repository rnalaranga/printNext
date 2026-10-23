package com.rr.secureprint.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "directory_config")
public class DirectoryConfigEntity {

    @Id
    private Long id = 1L;

    @Column(name = "directory_type", nullable = false, length = 32)
    private String directoryType = "ACTIVE_DIRECTORY";

    @Column(name = "ldap_url", nullable = false)
    private String ldapUrl = "ldap://127.0.0.1:389";

    @Column(name = "domain_name", nullable = false, length = 128)
    private String domainName = "qoa.local";

    @Column(name = "base_dn", nullable = false)
    private String baseDn = "DC=qoa,DC=local";

    @Column(name = "bind_user", length = 128)
    private String bindUser = "Administrator@qoa.local";

    @Column(name = "bind_password")
    private String bindPassword;

    @Column(name = "user_search_filter", length = 512)
    private String userSearchFilter = "(&(objectCategory=person)(objectClass=user))";

    @Column(name = "default_pin", length = 32)
    private String defaultPin = "1234";

    @Column(name = "is_enabled", nullable = false)
    private boolean isEnabled = true;

    @Column(name = "auto_sync_interval_mins")
    private Integer autoSyncIntervalMins = 60;

    @Column(name = "last_sync_at")
    private Instant lastSyncAt;

    @Column(name = "last_sync_status", length = 32)
    private String lastSyncStatus = "READY";

    @Column(name = "last_sync_message", length = 512)
    private String lastSyncMessage = "Active Directory (qoa.local) ready for synchronization.";

    @Column(name = "synced_user_count")
    private int syncedUserCount = 0;

    @Column(name = "created_at")
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt = Instant.now();

    public DirectoryConfigEntity() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getDirectoryType() { return directoryType; }
    public void setDirectoryType(String directoryType) { this.directoryType = directoryType; }

    public String getLdapUrl() { return ldapUrl; }
    public void setLdapUrl(String ldapUrl) { this.ldapUrl = ldapUrl; }

    public String getDomainName() { return domainName; }
    public void setDomainName(String domainName) { this.domainName = domainName; }

    public String getBaseDn() { return baseDn; }
    public void setBaseDn(String baseDn) { this.baseDn = baseDn; }

    public String getBindUser() { return bindUser; }
    public void setBindUser(String bindUser) { this.bindUser = bindUser; }

    public String getBindPassword() { return bindPassword; }
    public void setBindPassword(String bindPassword) { this.bindPassword = bindPassword; }

    public String getUserSearchFilter() { return userSearchFilter; }
    public void setUserSearchFilter(String userSearchFilter) { this.userSearchFilter = userSearchFilter; }

    public String getDefaultPin() { return defaultPin != null ? defaultPin : "1234"; }
    public void setDefaultPin(String defaultPin) { this.defaultPin = defaultPin; }

    public boolean isEnabled() { return isEnabled; }
    public void setEnabled(boolean enabled) { isEnabled = enabled; }

    public Integer getAutoSyncIntervalMins() { return autoSyncIntervalMins; }
    public void setAutoSyncIntervalMins(Integer autoSyncIntervalMins) { this.autoSyncIntervalMins = autoSyncIntervalMins; }

    public Instant getLastSyncAt() { return lastSyncAt; }
    public void setLastSyncAt(Instant lastSyncAt) { this.lastSyncAt = lastSyncAt; }

    public String getLastSyncStatus() { return lastSyncStatus; }
    public void setLastSyncStatus(String lastSyncStatus) { this.lastSyncStatus = lastSyncStatus; }

    public String getLastSyncMessage() { return lastSyncMessage; }
    public void setLastSyncMessage(String lastSyncMessage) { this.lastSyncMessage = lastSyncMessage; }

    public int getSyncedUserCount() { return syncedUserCount; }
    public void setSyncedUserCount(int syncedUserCount) { this.syncedUserCount = syncedUserCount; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
