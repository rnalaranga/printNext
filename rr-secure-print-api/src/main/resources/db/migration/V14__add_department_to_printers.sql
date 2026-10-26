-- Migration V14: Add department column to printers table for department-wise printer allocation
ALTER TABLE printers ADD COLUMN IF NOT EXISTS department VARCHAR(64) DEFAULT 'Operations';

-- Assign realistic enterprise departments to existing copiers
UPDATE printers SET department = 'Operations' WHERE ip_address LIKE '%192.168.1.10%' OR printer_code LIKE '%3518A%';
UPDATE printers SET department = 'Corporate' WHERE ip_address LIKE '%192.168.1.15%' OR ip_address LIKE '%192.168.1.18%' OR printer_code LIKE '%2518%' OR printer_code LIKE '%4508%';
UPDATE printers SET department = 'IT Administration' WHERE ip_address LIKE '%192.168.1.11%' OR printer_code LIKE '%306%';
UPDATE printers SET department = 'Finance' WHERE ip_address LIKE '%192.168.1.12%' OR printer_code LIKE '%256%';
