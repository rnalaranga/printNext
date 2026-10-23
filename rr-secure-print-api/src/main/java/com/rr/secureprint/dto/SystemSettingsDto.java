package com.rr.secureprint.dto;

import java.time.Instant;

public class SystemSettingsDto {

    private String organizationName;
    private String systemTitle;
    private String systemSubtitle;
    private String logoData;
    private String logoFilename;
    private String primaryColor;
    private Integer inactivityTimeoutSecs;
    private String supportContact;
    private Instant updatedAt;

    public SystemSettingsDto() {
    }

    public SystemSettingsDto(String organizationName, String systemTitle, String systemSubtitle,
                             String logoData, String logoFilename, String primaryColor,
                             Integer inactivityTimeoutSecs, String supportContact, Instant updatedAt) {
        this.organizationName = organizationName;
        this.systemTitle = systemTitle;
        this.systemSubtitle = systemSubtitle;
        this.logoData = logoData;
        this.logoFilename = logoFilename;
        this.primaryColor = primaryColor;
        this.inactivityTimeoutSecs = inactivityTimeoutSecs;
        this.supportContact = supportContact;
        this.updatedAt = updatedAt;
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
