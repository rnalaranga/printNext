-- Migration V16: Support distinct Toshiba MFP series (08 Series, 18 Series, 28 Series, Legacy)
-- Update existing printers based on their model number

UPDATE printers 
SET device_generation = 'TOSHIBA_08' 
WHERE (LOWER(model) LIKE '%08a%' OR LOWER(model) LIKE '%05ac%')
  AND (device_generation IS NULL OR device_generation = 'TOSHIBA_NEW');

UPDATE printers 
SET device_generation = 'TOSHIBA_18' 
WHERE (LOWER(model) LIKE '%18a%' OR LOWER(model) LIKE '%15ac%' OR LOWER(model) LIKE '%10ac%')
  AND (device_generation IS NULL OR device_generation = 'TOSHIBA_NEW');

UPDATE printers 
SET device_generation = 'TOSHIBA_28' 
WHERE (LOWER(model) LIKE '%28a%' OR LOWER(model) LIKE '%25ac%' OR LOWER(model) LIKE '%20ac%')
  AND (device_generation IS NULL OR device_generation = 'TOSHIBA_NEW');

UPDATE printers 
SET device_generation = 'TOSHIBA_OLD' 
WHERE (LOWER(model) LIKE '%306%' OR LOWER(model) LIKE '%256%' OR LOWER(model) LIKE '%356%' OR LOWER(model) LIKE '%456%' OR LOWER(model) LIKE '%506%')
  AND (device_generation IS NULL OR device_generation = 'TOSHIBA_NEW');
