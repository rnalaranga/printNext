CREATE TABLE IF NOT EXISTS printer_maintenance_logs (
    id BIGSERIAL PRIMARY KEY,
    printer_id BIGINT,
    printer_code VARCHAR(64),
    printer_name VARCHAR(160),
    printer_ip VARCHAR(64),
    maintenance_type VARCHAR(40) NOT NULL,
    toner_color VARCHAR(32) DEFAULT 'N/A',
    part_replaced VARCHAR(200),
    odometer_pages BIGINT DEFAULT 0,
    cost_lkr DOUBLE PRECISION DEFAULT 0.0,
    technician VARCHAR(120),
    vendor VARCHAR(120),
    status VARCHAR(32) NOT NULL DEFAULT 'COMPLETED',
    service_date VARCHAR(20) NOT NULL,
    next_service_date VARCHAR(20),
    notes TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
