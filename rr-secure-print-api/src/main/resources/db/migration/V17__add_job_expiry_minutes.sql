-- Migration V17: Add configurable held print job auto-delete expiry (minutes)
ALTER TABLE system_settings ADD COLUMN IF NOT EXISTS job_expiry_minutes INT DEFAULT 60;
