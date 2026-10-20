-- Migration: Add Virtual Queues and Virtual-to-Physical Printer Pools
CREATE TABLE IF NOT EXISTS virtual_queues (
    id BIGSERIAL PRIMARY KEY,
    queue_code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(128) NOT NULL,
    windows_printer_name VARCHAR(128) NOT NULL,
    share_name VARCHAR(128),
    driver_name VARCHAR(128) DEFAULT 'TOSHIBA Universal Printer 2',
    description VARCHAR(255),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS virtual_queue_printers (
    virtual_queue_id BIGINT NOT NULL REFERENCES virtual_queues(id) ON DELETE CASCADE,
    printer_id BIGINT NOT NULL REFERENCES printers(id) ON DELETE CASCADE,
    PRIMARY KEY (virtual_queue_id, printer_id)
);

-- Seed initial default virtual queue
INSERT INTO virtual_queues (queue_code, name, windows_printer_name, share_name, driver_name, description, is_active, is_default)
VALUES ('VQ-DEFAULT', 'General Find-Me Virtual Queue', 'QOA_Printer', 'QOA_Printer', 'TOSHIBA Universal Printer 2', 'Default Organization Pull Print Virtual Queue', TRUE, TRUE);

-- Link all existing active physical printers into the default virtual queue pool
INSERT INTO virtual_queue_printers (virtual_queue_id, printer_id)
SELECT (SELECT id FROM virtual_queues WHERE queue_code = 'VQ-DEFAULT' LIMIT 1), id
FROM printers WHERE is_active = TRUE;
