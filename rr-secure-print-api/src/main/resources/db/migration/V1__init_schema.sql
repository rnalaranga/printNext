-- =============================================================================
-- RR Secure Print Database Schema
-- Version: 1.0.0
-- Compatible with PostgreSQL 13+ and H2 (PostgreSQL Mode)
-- =============================================================================

-- 1. Users Table (Maps Active Directory user SIDs, PINs, and Badge Cards)
CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(64) NOT NULL UNIQUE,
    display_name VARCHAR(128) NOT NULL,
    user_sid VARCHAR(128) NOT NULL UNIQUE,
    pin_hash VARCHAR(255),
    badge_id VARCHAR(64) UNIQUE,
    email VARCHAR(128),
    department VARCHAR(64),
    role VARCHAR(32) NOT NULL DEFAULT 'USER',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 2. Printers Table (Toshiba e-STUDIO MFPs and Managed Queues)
CREATE TABLE IF NOT EXISTS printers (
    id BIGSERIAL PRIMARY KEY,
    printer_code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(128) NOT NULL,
    ip_address VARCHAR(64) NOT NULL,
    raw_port INT NOT NULL DEFAULT 9100,
    location VARCHAR(128) NOT NULL,
    model VARCHAR(64) DEFAULT 'Toshiba e-STUDIO 3518A',
    color_supported BOOLEAN NOT NULL DEFAULT FALSE,
    duplex_supported BOOLEAN NOT NULL DEFAULT TRUE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    status VARCHAR(32) NOT NULL DEFAULT 'ONLINE',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 3. Print Jobs Table
-- Statuses: RECEIVED, HELD, RELEASING, SUBMITTED, COMPLETED, CANCELLED, EXPIRED, FAILED
CREATE TABLE IF NOT EXISTS print_jobs (
    id UUID PRIMARY KEY,
    job_number BIGSERIAL UNIQUE,
    user_id BIGINT REFERENCES users(id),
    user_sid VARCHAR(128) NOT NULL,
    username VARCHAR(64) NOT NULL,
    document_name VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL,
    page_count INT NOT NULL DEFAULT 1,
    color BOOLEAN NOT NULL DEFAULT FALSE,
    duplex BOOLEAN NOT NULL DEFAULT TRUE,
    copies INT NOT NULL DEFAULT 1,
    file_size_bytes BIGINT NOT NULL DEFAULT 0,
    spool_file_path VARCHAR(512) NOT NULL,
    checksum_sha256 VARCHAR(64) NOT NULL,
    client_machine VARCHAR(128),
    windows_job_id INT,
    target_printer_id BIGINT REFERENCES printers(id),
    submitted_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    released_at TIMESTAMP WITH TIME ZONE,
    expires_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0,
    error_message TEXT
);

-- 4. Audit Logs Table (Full Traceability for Security & Compliance)
CREATE TABLE IF NOT EXISTS audit_logs (
    id BIGSERIAL PRIMARY KEY,
    timestamp TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    event_type VARCHAR(64) NOT NULL,
    user_sid VARCHAR(128),
    username VARCHAR(64),
    job_id UUID,
    printer_code VARCHAR(64),
    ip_address VARCHAR(64),
    action_details TEXT,
    status VARCHAR(32) NOT NULL DEFAULT 'SUCCESS'
);

-- Indexes for high-performance pull-print lookups and lifecycle management
CREATE INDEX IF NOT EXISTS idx_users_sid ON users(user_sid);
CREATE INDEX IF NOT EXISTS idx_users_pin ON users(pin_hash);
CREATE INDEX IF NOT EXISTS idx_users_badge ON users(badge_id);

CREATE INDEX IF NOT EXISTS idx_print_jobs_user_sid_status ON print_jobs(user_sid, status);
CREATE INDEX IF NOT EXISTS idx_print_jobs_status_expires ON print_jobs(status, expires_at);
CREATE INDEX IF NOT EXISTS idx_print_jobs_submitted_at ON print_jobs(submitted_at);

CREATE INDEX IF NOT EXISTS idx_audit_logs_timestamp ON audit_logs(timestamp);
CREATE INDEX IF NOT EXISTS idx_audit_logs_username ON audit_logs(username);
CREATE INDEX IF NOT EXISTS idx_audit_logs_event_type ON audit_logs(event_type);

-- Initial Seed Data: Printer (Single Toshiba e-STUDIO 3518A at 192.168.1.8)
INSERT INTO printers (printer_code, name, ip_address, raw_port, location, model, color_supported, duplex_supported, is_active, status)
SELECT 'TOSHIBA-3518A-HQ', 'Toshiba e-STUDIO 3518A', '192.168.1.8', 9100, 'Main Office', 'Toshiba e-STUDIO 3518A', FALSE, TRUE, TRUE, 'ONLINE'
WHERE NOT EXISTS (SELECT 1 FROM printers WHERE printer_code = 'TOSHIBA-3518A-HQ');

-- Initial Seed Data: Standard Users & PINs (Real User RR & Admin)
INSERT INTO users (username, display_name, user_sid, pin_hash, badge_id, email, department, role)
SELECT 'admin', 'RR Administrator', 'S-1-5-21-3623811015-3361044348-30300820-1000', '9999', 'BADGE-0001', 'admin@rrconstruction.local', 'IT Administration', 'ADMIN'
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'admin');

INSERT INTO users (username, display_name, user_sid, pin_hash, badge_id, email, department, role)
SELECT 'rr', 'RR', 'S-1-5-21-1966046810-3588821400-173210280-1001', '1234', 'BADGE-1001', 'rr@rrconstruction.local', 'Operations', 'USER'
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'rr');
