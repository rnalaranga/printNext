package com.rr.secureprint.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "users")
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String username;

    @Column(name = "display_name", nullable = false, length = 128)
    private String displayName;

    @Column(name = "user_sid", nullable = false, unique = true, length = 128)
    private String userSid;

    @Column(name = "pin_hash", length = 255)
    private String pinHash;

    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Column(name = "badge_id", unique = true, length = 64)
    private String badgeId;

    @Column(name = "secondary_badge_id", length = 64)
    private String secondaryBadgeId;

    @Column(length = 128)
    private String email;

    @Column(length = 64)
    private String department;

    @Column(nullable = false, length = 32)
    private String role = "USER";

    @Column(name = "auth_source", length = 32)
    private String authSource = "MANUAL";

    @Column(name = "external_attr_1", length = 128)
    private String externalAttribute1;

    @Column(name = "external_attr_2", length = 128)
    private String externalAttribute2;

    @Column(name = "external_attr_3", length = 128)
    private String externalAttribute3;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @Column(name = "print_count", nullable = false)
    private long printCount = 0L;

    @Column(name = "copy_count", nullable = false)
    private long copyCount = 0L;

    @Column(name = "page_quota")
    private Integer pageQuota;

    @Column(name = "created_at")
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt = Instant.now();

    public UserEntity() {
    }

    public UserEntity(String username, String displayName, String userSid, String pinHash, String badgeId, String email, String department, String role) {
        this(username, displayName, userSid, pinHash, badgeId, email, department, role, "MANUAL");
    }

    public UserEntity(String username, String displayName, String userSid, String pinHash, String badgeId, String email, String department, String role, String authSource) {
        this.username = username;
        this.displayName = displayName;
        this.userSid = userSid;
        this.pinHash = pinHash;
        this.badgeId = badgeId;
        this.email = email;
        this.department = department;
        this.role = role != null ? role : "USER";
        this.authSource = authSource != null ? authSource : "MANUAL";
        this.isActive = true;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getUserSid() { return userSid; }
    public void setUserSid(String userSid) { this.userSid = userSid; }

    public String getPinHash() { return pinHash; }
    public void setPinHash(String pinHash) { this.pinHash = pinHash; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public String getBadgeId() { return badgeId; }
    public void setBadgeId(String badgeId) { this.badgeId = badgeId; }

    public String getSecondaryBadgeId() { return secondaryBadgeId; }
    public void setSecondaryBadgeId(String secondaryBadgeId) { this.secondaryBadgeId = secondaryBadgeId; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getAuthSource() { return authSource != null ? authSource : "MANUAL"; }
    public void setAuthSource(String authSource) { this.authSource = authSource; }

    public String getExternalAttribute1() { return externalAttribute1; }
    public void setExternalAttribute1(String externalAttribute1) { this.externalAttribute1 = externalAttribute1; }

    public String getExternalAttribute2() { return externalAttribute2; }
    public void setExternalAttribute2(String externalAttribute2) { this.externalAttribute2 = externalAttribute2; }

    public String getExternalAttribute3() { return externalAttribute3; }
    public void setExternalAttribute3(String externalAttribute3) { this.externalAttribute3 = externalAttribute3; }

    public String getCostCenter() {
        return externalAttribute3 != null && !externalAttribute3.isBlank() ? externalAttribute3 : "Unassigned";
    }

    public void setCostCenter(String costCenter) {
        this.externalAttribute3 = costCenter;
    }

    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }

    public long getPrintCount() { return printCount; }
    public void setPrintCount(long printCount) { this.printCount = printCount; }

    public long getCopyCount() { return copyCount; }
    public void setCopyCount(long copyCount) { this.copyCount = copyCount; }

    public long getTotalPages() { return printCount + copyCount; }

    public Integer getPageQuota() { return pageQuota; }
    public void setPageQuota(Integer pageQuota) { this.pageQuota = pageQuota; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
