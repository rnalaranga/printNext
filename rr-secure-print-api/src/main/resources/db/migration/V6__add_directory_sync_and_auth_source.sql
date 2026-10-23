-- =============================================================================
-- Migration V6: Active Directory / LDAP Synchronization & User Identity Sources
-- PaperCut MF Architecture: Enterprise Directory Mapping & Find-Me JIT Provisioning
-- =============================================================================

-- 1. Add auth_source column to users table
ALTER TABLE users ADD COLUMN IF NOT EXISTS auth_source VARCHAR(32) DEFAULT 'MANUAL';

-- 2. Create directory_config table for Active Directory & LDAP configuration
CREATE TABLE IF NOT EXISTS directory_config (
    id BIGINT PRIMARY KEY,
    directory_type VARCHAR(32) NOT NULL DEFAULT 'ACTIVE_DIRECTORY',
    ldap_url VARCHAR(255) NOT NULL DEFAULT 'ldap://127.0.0.1:389',
    domain_name VARCHAR(128) NOT NULL DEFAULT 'qoa.local',
    base_dn VARCHAR(255) NOT NULL DEFAULT 'DC=qoa,DC=local',
    bind_user VARCHAR(128) DEFAULT 'Administrator@qoa.local',
    bind_password VARCHAR(255),
    user_search_filter VARCHAR(512) DEFAULT '(&(objectCategory=person)(objectClass=user))',
    default_pin VARCHAR(32) DEFAULT '1234',
    is_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    auto_sync_interval_mins INT DEFAULT 60,
    last_sync_at TIMESTAMP WITH TIME ZONE,
    last_sync_status VARCHAR(32) DEFAULT 'READY',
    last_sync_message VARCHAR(512) DEFAULT 'Active Directory (qoa.local) ready for synchronization.',
    synced_user_count INT DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 3. Seed default Active Directory configuration
INSERT INTO directory_config (
    id, directory_type, ldap_url, domain_name, base_dn, bind_user,
    user_search_filter, default_pin, is_enabled, last_sync_status, last_sync_message
) SELECT
    1, 'ACTIVE_DIRECTORY', 'ldap://127.0.0.1:389', 'qoa.local', 'DC=qoa,DC=local', 'Administrator@qoa.local',
    '(&(objectCategory=person)(objectClass=user))', '1234', TRUE, 'READY', 'Active Directory (qoa.local) ready for synchronization.'
WHERE NOT EXISTS (SELECT 1 FROM directory_config WHERE id = 1);
