ALTER TABLE printer_maintenance_logs ADD COLUMN IF NOT EXISTS part_number VARCHAR(80);
ALTER TABLE printer_maintenance_logs ADD COLUMN IF NOT EXISTS part_serial VARCHAR(80);
