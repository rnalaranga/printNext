-- Migration V12: Add secondary badge ID for NFC / multi-card support
ALTER TABLE users ADD COLUMN IF NOT EXISTS secondary_badge_id VARCHAR(64);
CREATE INDEX IF NOT EXISTS idx_users_secondary_badge_id ON users(secondary_badge_id);
