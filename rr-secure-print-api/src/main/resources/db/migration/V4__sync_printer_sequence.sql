-- Synchronize printers table identity sequence to avoid collision with pre-seeded IDs
ALTER TABLE printers ALTER COLUMN id RESTART WITH 100;
