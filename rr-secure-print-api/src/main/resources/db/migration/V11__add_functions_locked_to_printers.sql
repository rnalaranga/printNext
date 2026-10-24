-- Migration V11: Add functions_locked boolean to printers table
ALTER TABLE printers ADD COLUMN IF NOT EXISTS functions_locked BOOLEAN DEFAULT FALSE;
