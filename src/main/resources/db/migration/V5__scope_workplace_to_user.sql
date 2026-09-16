ALTER TABLE workplace
    ADD COLUMN user_id BINARY(16) NOT NULL AFTER id,
    ADD CONSTRAINT fk_workplace_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    MODIFY road_address VARCHAR(255) NOT NULL,
    DROP COLUMN address,
    DROP COLUMN transport_type,
    DROP COLUMN max_commute_minutes;
