-- =============================================================================
-- Migration V10: System Settings & Organization Branding
-- Supports custom corporate logo, organization name, system title & UI preferences
-- =============================================================================

CREATE TABLE IF NOT EXISTS system_settings (
    id BIGINT PRIMARY KEY,
    organization_name VARCHAR(255) DEFAULT 'RR Secure Print',
    system_title VARCHAR(128) DEFAULT 'nexPrint',
    system_subtitle VARCHAR(255) DEFAULT 'Print Management',
    logo_data TEXT,
    logo_filename VARCHAR(255),
    primary_color VARCHAR(32) DEFAULT '#1D4ED8',
    inactivity_timeout_secs INT DEFAULT 60,
    support_contact VARCHAR(255) DEFAULT 'IT Helpdesk',
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Seed default initial row if not already present
INSERT INTO system_settings (
    id, organization_name, system_title, system_subtitle,
    logo_data, logo_filename, primary_color, inactivity_timeout_secs, support_contact
)
SELECT 
    1, 'RR Secure Print', 'nexPrint', 'Print Management',
    NULL, NULL, '#1D4ED8', 60, 'IT Helpdesk'
WHERE NOT EXISTS (SELECT 1 FROM system_settings WHERE id = 1);
