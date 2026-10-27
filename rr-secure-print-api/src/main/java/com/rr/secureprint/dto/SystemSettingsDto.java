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

    // SMTP & Scan settings
    private String smtpHost;
    private Integer smtpPort;
    private String smtpUsername;
    private String smtpPassword;
    private String smtpFromAddress;
    private String smtpFromName;
    private Boolean smtpUseTls;
    private Boolean smtpUseSsl;
    private Boolean scanToEmailEnabled;
    private String defaultScanSubject;

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

    public String getOrganizationName() { return organizationName; }
    public void setOrganizationName(String organizationName) { this.organizationName = organizationName; }

    public String getSystemTitle() { return systemTitle; }
    public void setSystemTitle(String systemTitle) { this.systemTitle = systemTitle; }

    public String getSystemSubtitle() { return systemSubtitle; }
    public void setSystemSubtitle(String systemSubtitle) { this.systemSubtitle = systemSubtitle; }

    public String getLogoData() { return logoData; }
    public void setLogoData(String logoData) { this.logoData = logoData; }

    public String getLogoFilename() { return logoFilename; }
    public void setLogoFilename(String logoFilename) { this.logoFilename = logoFilename; }

    public String getPrimaryColor() { return primaryColor; }
    public void setPrimaryColor(String primaryColor) { this.primaryColor = primaryColor; }

    public Integer getInactivityTimeoutSecs() { return inactivityTimeoutSecs; }
    public void setInactivityTimeoutSecs(Integer inactivityTimeoutSecs) { this.inactivityTimeoutSecs = inactivityTimeoutSecs; }

    public String getSupportContact() { return supportContact; }
    public void setSupportContact(String supportContact) { this.supportContact = supportContact; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public String getSmtpHost() { return smtpHost; }
    public void setSmtpHost(String smtpHost) { this.smtpHost = smtpHost; }

    public Integer getSmtpPort() { return smtpPort; }
    public void setSmtpPort(Integer smtpPort) { this.smtpPort = smtpPort; }

    public String getSmtpUsername() { return smtpUsername; }
    public void setSmtpUsername(String smtpUsername) { this.smtpUsername = smtpUsername; }

    public String getSmtpPassword() { return smtpPassword; }
    public void setSmtpPassword(String smtpPassword) { this.smtpPassword = smtpPassword; }

    public String getSmtpFromAddress() { return smtpFromAddress; }
    public void setSmtpFromAddress(String smtpFromAddress) { this.smtpFromAddress = smtpFromAddress; }

    public String getSmtpFromName() { return smtpFromName; }
    public void setSmtpFromName(String smtpFromName) { this.smtpFromName = smtpFromName; }

    public Boolean getSmtpUseTls() { return smtpUseTls; }
    public void setSmtpUseTls(Boolean smtpUseTls) { this.smtpUseTls = smtpUseTls; }

    public Boolean getSmtpUseSsl() { return smtpUseSsl; }
    public void setSmtpUseSsl(Boolean smtpUseSsl) { this.smtpUseSsl = smtpUseSsl; }

    public Boolean getScanToEmailEnabled() { return scanToEmailEnabled; }
    public void setScanToEmailEnabled(Boolean scanToEmailEnabled) { this.scanToEmailEnabled = scanToEmailEnabled; }

    public String getDefaultScanSubject() { return defaultScanSubject; }
    public void setDefaultScanSubject(String defaultScanSubject) { this.defaultScanSubject = defaultScanSubject; }
}
