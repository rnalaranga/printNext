ALTER TABLE printer_maintenance_logs ADD COLUMN IF NOT EXISTS started_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE printer_maintenance_logs ADD COLUMN IF NOT EXISTS completed_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE printer_maintenance_logs ADD COLUMN IF NOT EXISTS duration_minutes BIGINT DEFAULT 0;
