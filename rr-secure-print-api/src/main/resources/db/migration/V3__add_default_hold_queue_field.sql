-- Migration: Add Default Virtual / Hold Queue field to printers table
ALTER TABLE printers ADD COLUMN IF NOT EXISTS is_default_hold_queue BOOLEAN DEFAULT FALSE;

-- Automatically set the first active printer as the default hold queue if none is set
UPDATE printers SET is_default_hold_queue = TRUE WHERE id = (SELECT id FROM printers ORDER BY id ASC LIMIT 1);
