-- =============================================================================
-- Migration V7: Add password_hash column to users table for manual & local accounts
-- =============================================================================

ALTER TABLE users ADD COLUMN IF NOT EXISTS password_hash VARCHAR(255);

-- Seed default passwords for built-in demo accounts (password: admin123, rr123)
UPDATE users SET password_hash = 'admin123' WHERE username = 'admin' AND password_hash IS NULL;
UPDATE users SET password_hash = 'rr123' WHERE username = 'rr' AND password_hash IS NULL;
