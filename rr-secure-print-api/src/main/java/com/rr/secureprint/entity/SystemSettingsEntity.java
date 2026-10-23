package com.rr.secureprint.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "system_settings")
public class SystemSettingsEntity {

    @Id
    private Long id = 1L;

    @Column(name = "organization_name", length = 255)
    private String organizationName = "RR Secure Print";

    @Column(name = "system_title", length = 128)
    private String systemTitle = "nexPrint";

    @Column(name = "system_subtitle", length = 255)
    private String systemSubtitle = "Print Management";

    @Column(name = "logo_data", columnDefinition = "TEXT")
    private String logoData;

    @Column(name = "logo_filename", length = 255)
    private String logoFilename;

    @Column(name = "primary_color", length = 32)
    private String primaryColor = "#1D4ED8";

    @Column(name = "inactivity_timeout_secs")
    private Integer inactivityTimeoutSecs = 60;

    @Column(name = "support_contact", length = 255)
    private String supportContact = "IT Helpdesk";

    @Column(name = "updated_at")
    private Instant updatedAt = Instant.now();

    public SystemSettingsEntity() {
    }

    public SystemSettingsEntity(Long id) {
        this.id = id;
    }

    // Getters and Setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getOrganizationName() {
        return organizationName;
    }

    public void setOrganizationName(String organizationName) {
        this.organizationName = organizationName;
    }

    public String getSystemTitle() {
        return systemTitle;
    }

    public void setSystemTitle(String systemTitle) {
        this.systemTitle = systemTitle;
    }

    public String getSystemSubtitle() {
        return systemSubtitle;
    }

    public void setSystemSubtitle(String systemSubtitle) {
        this.systemSubtitle = systemSubtitle;
    }

    public String getLogoData() {
        return logoData;
    }

    public void setLogoData(String logoData) {
        this.logoData = logoData;
    }

    public String getLogoFilename() {
        return logoFilename;
    }

    public void setLogoFilename(String logoFilename) {
        this.logoFilename = logoFilename;
    }

    public String getPrimaryColor() {
        return primaryColor;
    }

    public void setPrimaryColor(String primaryColor) {
        this.primaryColor = primaryColor;
    }

    public Integer getInactivityTimeoutSecs() {
        return inactivityTimeoutSecs;
    }

    public void setInactivityTimeoutSecs(Integer inactivityTimeoutSecs) {
        this.inactivityTimeoutSecs = inactivityTimeoutSecs;
    }

    public String getSupportContact() {
        return supportContact;
    }

    public void setSupportContact(String supportContact) {
        this.supportContact = supportContact;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
