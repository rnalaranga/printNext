-- Migration: Add Toshiba EWB configuration fields to printers table
ALTER TABLE printers ADD COLUMN IF NOT EXISTS admin_username VARCHAR(64) DEFAULT 'admin';
ALTER TABLE printers ADD COLUMN IF NOT EXISTS admin_password VARCHAR(128) DEFAULT '123456';
ALTER TABLE printers ADD COLUMN IF NOT EXISTS ewb_configured BOOLEAN DEFAULT FALSE;
ALTER TABLE printers ADD COLUMN IF NOT EXISTS ewb_url VARCHAR(255);
ALTER TABLE printers ADD COLUMN IF NOT EXISTS ewb_configured_at TIMESTAMP WITH TIME ZONE;
