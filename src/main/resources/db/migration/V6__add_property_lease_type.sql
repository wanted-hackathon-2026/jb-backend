ALTER TABLE property
    ADD COLUMN lease_type VARCHAR(20) NULL AFTER property_type;

-- Legacy rows did not distinguish lease types. Classify them from the rent,
-- preserving all existing IDs and property values. Review zero-rent rows.
UPDATE property
SET lease_type = CASE WHEN monthly_rent = 0 THEN 'JEONSE' ELSE 'MONTHLY' END;

ALTER TABLE property
    MODIFY lease_type VARCHAR(20) NOT NULL,
    ADD CONSTRAINT chk_property_lease_price CHECK (
        (lease_type = 'JEONSE' AND monthly_rent = 0)
        OR (lease_type = 'MONTHLY' AND monthly_rent > 0)
    );
