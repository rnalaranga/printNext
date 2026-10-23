-- Migration V9: Add Device Generation (TOSHIBA_NEW / TOSHIBA_OLD) to printers table
ALTER TABLE printers ADD COLUMN IF NOT EXISTS device_generation VARCHAR(32) DEFAULT 'TOSHIBA_NEW';

-- Backfill existing legacy printers if their model indicates e-BRIDGE legacy (306, 256, 356, 456, 506, 305, etc.)
UPDATE printers 
SET device_generation = 'TOSHIBA_OLD' 
WHERE LOWER(model) LIKE '%306%' 
   OR LOWER(model) LIKE '%256%' 
   OR LOWER(model) LIKE '%356%' 
   OR LOWER(model) LIKE '%456%' 
   OR LOWER(model) LIKE '%506%'
   OR LOWER(model) LIKE '%305%';
