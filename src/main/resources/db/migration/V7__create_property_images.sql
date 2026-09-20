CREATE TABLE property_image
(
    id            BINARY(16)   NOT NULL,
    property_id   BINARY(16)   NOT NULL,
    storage_key   VARCHAR(255) NOT NULL,
    display_order INT          NOT NULL,
    created_at    DATETIME(6)  NOT NULL,
    CONSTRAINT pk_property_image PRIMARY KEY (id),
    CONSTRAINT uk_property_image_storage_key UNIQUE (storage_key),
    CONSTRAINT uk_property_image_order UNIQUE (property_id, display_order),
    CONSTRAINT fk_property_image_property FOREIGN KEY (property_id) REFERENCES property (id) ON DELETE CASCADE
);
