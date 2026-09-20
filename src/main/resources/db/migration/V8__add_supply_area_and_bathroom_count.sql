ALTER TABLE property
    ADD COLUMN supply_area DECIMAL(8, 2) NULL AFTER exclusive_area,
    ADD COLUMN bathroom_count INT NULL AFTER floor;
