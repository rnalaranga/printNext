-- =============================================================================
-- Migration V13: Add externalAttributes (1, 2, 3) and LDAP attribute mappings
-- PaperCut MF Enterprise Architecture: Flexible Active Directory Attribute Mapping
-- =============================================================================

ALTER TABLE users ADD COLUMN IF NOT EXISTS external_attr_1 VARCHAR(128);
ALTER TABLE users ADD COLUMN IF NOT EXISTS external_attr_2 VARCHAR(128);
ALTER TABLE users ADD COLUMN IF NOT EXISTS external_attr_3 VARCHAR(128);

ALTER TABLE directory_config ADD COLUMN IF NOT EXISTS card_id_attribute VARCHAR(64) DEFAULT 'employeeNumber';
ALTER TABLE directory_config ADD COLUMN IF NOT EXISTS secondary_card_attribute VARCHAR(64) DEFAULT 'extensionAttribute2';
ALTER TABLE directory_config ADD COLUMN IF NOT EXISTS external_attr_1_name VARCHAR(64) DEFAULT 'extensionAttribute1';
ALTER TABLE directory_config ADD COLUMN IF NOT EXISTS external_attr_2_name VARCHAR(64) DEFAULT 'extensionAttribute2';
ALTER TABLE directory_config ADD COLUMN IF NOT EXISTS external_attr_3_name VARCHAR(64) DEFAULT 'extensionAttribute3';
ALTER TABLE directory_config ADD COLUMN IF NOT EXISTS username_attribute VARCHAR(64) DEFAULT 'sAMAccountName';
ALTER TABLE directory_config ADD COLUMN IF NOT EXISTS display_name_attribute VARCHAR(64) DEFAULT 'displayName';
ALTER TABLE directory_config ADD COLUMN IF NOT EXISTS email_attribute VARCHAR(64) DEFAULT 'mail';
ALTER TABLE directory_config ADD COLUMN IF NOT EXISTS department_attribute VARCHAR(64) DEFAULT 'department';
