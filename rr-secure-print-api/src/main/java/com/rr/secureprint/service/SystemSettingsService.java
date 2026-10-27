package com.rr.secureprint.service;

import com.rr.secureprint.dto.SystemSettingsDto;
import com.rr.secureprint.entity.SystemSettingsEntity;
import com.rr.secureprint.repository.SystemSettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

@Service
public class SystemSettingsService {

    private static final Logger LOGGER = Logger.getLogger(SystemSettingsService.class.getName());
    private static final Long DEFAULT_SETTINGS_ID = 1L;

    private final SystemSettingsRepository repository;
    private final AuditService auditService;

    public SystemSettingsService(SystemSettingsRepository repository, AuditService auditService) {
        this.repository = repository;
        this.auditService = auditService;
    }

    /**
     * Retrieves the current system settings, ensuring default row exists.
     */
    @Transactional
    public SystemSettingsEntity getOrCreateSettings() {
        return repository.findById(DEFAULT_SETTINGS_ID).orElseGet(() -> {
            SystemSettingsEntity defaults = new SystemSettingsEntity(DEFAULT_SETTINGS_ID);
            defaults.setOrganizationName("RR Secure Print");
            defaults.setSystemTitle("nexPrint");
            defaults.setSystemSubtitle("Print Management");
            defaults.setPrimaryColor("#1D4ED8");
            defaults.setInactivityTimeoutSecs(60);
            defaults.setSupportContact("IT Helpdesk");
            defaults.setUpdatedAt(Instant.now());
            return repository.save(defaults);
        });
    }

    /**
     * Converts entity to DTO for API responses.
     */
    public SystemSettingsDto toDto(SystemSettingsEntity entity) {
        SystemSettingsDto dto = new SystemSettingsDto(
                entity.getOrganizationName(),
                entity.getSystemTitle(),
                entity.getSystemSubtitle(),
                entity.getLogoData(),
                entity.getLogoFilename(),
                entity.getPrimaryColor(),
                entity.getInactivityTimeoutSecs(),
                entity.getSupportContact(),
                entity.getUpdatedAt()
        );
        dto.setSmtpHost(entity.getSmtpHost());
        dto.setSmtpPort(entity.getSmtpPort());
        dto.setSmtpUsername(entity.getSmtpUsername());
        dto.setSmtpPassword(entity.getSmtpPassword());
        dto.setSmtpFromAddress(entity.getSmtpFromAddress());
        dto.setSmtpFromName(entity.getSmtpFromName());
        dto.setSmtpUseTls(entity.getSmtpUseTls());
        dto.setSmtpUseSsl(entity.getSmtpUseSsl());
        dto.setScanToEmailEnabled(entity.getScanToEmailEnabled());
        dto.setDefaultScanSubject(entity.getDefaultScanSubject());
        return dto;
    }

    /**
     * Updates general organization settings.
     */
    @Transactional
    public SystemSettingsEntity updateSettings(SystemSettingsDto dto, String adminActor, String clientIp) {
        SystemSettingsEntity settings = getOrCreateSettings();

        if (dto.getOrganizationName() != null && !dto.getOrganizationName().isBlank()) {
            settings.setOrganizationName(dto.getOrganizationName().trim());
        }
        if (dto.getSystemTitle() != null && !dto.getSystemTitle().isBlank()) {
            settings.setSystemTitle(dto.getSystemTitle().trim());
        }
        if (dto.getSystemSubtitle() != null) {
            settings.setSystemSubtitle(dto.getSystemSubtitle().trim());
        }
        if (dto.getPrimaryColor() != null && !dto.getPrimaryColor().isBlank()) {
            settings.setPrimaryColor(dto.getPrimaryColor().trim());
        }
        if (dto.getInactivityTimeoutSecs() != null && dto.getInactivityTimeoutSecs() >= 10 && dto.getInactivityTimeoutSecs() <= 3600) {
            settings.setInactivityTimeoutSecs(dto.getInactivityTimeoutSecs());
        }
        if (dto.getSupportContact() != null) {
            settings.setSupportContact(dto.getSupportContact().trim());
        }

        if (dto.getSmtpHost() != null) {
            settings.setSmtpHost(dto.getSmtpHost().trim());
        }
        if (dto.getSmtpPort() != null && dto.getSmtpPort() > 0 && dto.getSmtpPort() <= 65535) {
            settings.setSmtpPort(dto.getSmtpPort());
        }
        if (dto.getSmtpUsername() != null) {
            settings.setSmtpUsername(dto.getSmtpUsername().trim());
        }
        if (dto.getSmtpPassword() != null) {
            settings.setSmtpPassword(dto.getSmtpPassword());
        }
        if (dto.getSmtpFromAddress() != null && !dto.getSmtpFromAddress().isBlank()) {
            settings.setSmtpFromAddress(dto.getSmtpFromAddress().trim());
        }
        if (dto.getSmtpFromName() != null) {
            settings.setSmtpFromName(dto.getSmtpFromName().trim());
        }
        if (dto.getSmtpUseTls() != null) {
            settings.setSmtpUseTls(dto.getSmtpUseTls());
        }
        if (dto.getSmtpUseSsl() != null) {
            settings.setSmtpUseSsl(dto.getSmtpUseSsl());
        }
        if (dto.getScanToEmailEnabled() != null) {
            settings.setScanToEmailEnabled(dto.getScanToEmailEnabled());
        }
        if (dto.getDefaultScanSubject() != null && !dto.getDefaultScanSubject().isBlank()) {
            settings.setDefaultScanSubject(dto.getDefaultScanSubject().trim());
        }

        settings.setUpdatedAt(Instant.now());
        SystemSettingsEntity saved = repository.save(settings);

        auditService.logEvent(
                "SYSTEM_SETTINGS_UPDATED",
                null,
                adminActor != null ? adminActor : "admin",
                null,
                null,
                clientIp,
                "Updated organization settings: " + saved.getOrganizationName() + " / " + saved.getSystemTitle(),
                "SUCCESS"
        );

        LOGGER.info("[SETTINGS] System settings updated by " + adminActor + " (" + clientIp + ")");
        return saved;
    }

    /**
     * Updates or uploads custom logo data (Base64 data URL).
     */
    @Transactional
    public SystemSettingsEntity updateLogo(String logoData, String filename, String adminActor, String clientIp) {
        if (logoData == null || logoData.isBlank()) {
            throw new IllegalArgumentException("Logo data cannot be empty.");
        }

        // Basic check for image data URL format or base64
        if (!logoData.startsWith("data:image/") && !logoData.startsWith("http")) {
            throw new IllegalArgumentException("Invalid image format. Must be a valid image Data URL.");
        }

        SystemSettingsEntity settings = getOrCreateSettings();
        settings.setLogoData(logoData);
        settings.setLogoFilename(filename != null && !filename.isBlank() ? filename : "custom-logo.png");
        settings.setUpdatedAt(Instant.now());

        SystemSettingsEntity saved = repository.save(settings);

        auditService.logEvent(
                "SYSTEM_LOGO_UPDATED",
                null,
                adminActor != null ? adminActor : "admin",
                null,
                null,
                clientIp,
                "Uploaded custom organization logo: " + settings.getLogoFilename(),
                "SUCCESS"
        );

        LOGGER.info("[SETTINGS] Custom logo updated by " + adminActor + " (" + clientIp + ")");
        return saved;
    }

    /**
     * Resets logo back to system default.
     */
    @Transactional
    public SystemSettingsEntity resetLogo(String adminActor, String clientIp) {
        SystemSettingsEntity settings = getOrCreateSettings();
        settings.setLogoData(null);
        settings.setLogoFilename(null);
        settings.setUpdatedAt(Instant.now());

        SystemSettingsEntity saved = repository.save(settings);

        auditService.logEvent(
                "SYSTEM_LOGO_RESET",
                null,
                adminActor != null ? adminActor : "admin",
                null,
                null,
                clientIp,
                "Reset organization logo to system default",
                "SUCCESS"
        );

        LOGGER.info("[SETTINGS] Organization logo reset to default by " + adminActor + " (" + clientIp + ")");
        return saved;
    }

    /**
     * Returns lightweight public branding info (for terminals, login page, and headers).
     */
    public Map<String, Object> getPublicBranding() {
        SystemSettingsEntity settings = getOrCreateSettings();
        Map<String, Object> branding = new HashMap<>();
        branding.put("organizationName", settings.getOrganizationName());
        branding.put("systemTitle", settings.getSystemTitle());
        branding.put("systemSubtitle", settings.getSystemSubtitle());
        branding.put("hasCustomLogo", settings.getLogoData() != null && !settings.getLogoData().isBlank());
        branding.put("logoData", settings.getLogoData());
        branding.put("logoFilename", settings.getLogoFilename());
        branding.put("primaryColor", settings.getPrimaryColor());
        branding.put("inactivityTimeoutSecs", settings.getInactivityTimeoutSecs());
        branding.put("supportContact", settings.getSupportContact());
        return branding;
    }
}
