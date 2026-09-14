CREATE TABLE workplace
(
    id                  BINARY(16)   NOT NULL,
    name                VARCHAR(50)  NOT NULL,
    address             VARCHAR(255) NOT NULL,
    road_address        VARCHAR(255) NULL,
    lat                 DOUBLE       NOT NULL,
    lng                 DOUBLE       NOT NULL,
    transport_type      VARCHAR(20)  NOT NULL,
    max_commute_minutes INT          NOT NULL,
    created_at          DATETIME(6)  NOT NULL,
    updated_at          DATETIME(6)  NOT NULL,
    CONSTRAINT pk_workplace PRIMARY KEY (id)
);

CREATE TABLE property
(
    id             BINARY(16)    NOT NULL,
    name           VARCHAR(100)  NOT NULL,
    address        VARCHAR(255)  NOT NULL,
    road_address   VARCHAR(255)  NULL,
    sgg_code       VARCHAR(20)   NOT NULL,
    umd_name       VARCHAR(50)   NOT NULL,
    lat            DOUBLE        NOT NULL,
    lng            DOUBLE        NOT NULL,
    property_type  VARCHAR(20)   NOT NULL,
    deposit        INT           NOT NULL,
    monthly_rent   INT           NOT NULL,
    exclusive_area DECIMAL(8, 2) NULL,
    floor          INT           NULL,
    total_floors   INT           NULL,
    build_year     INT           NULL,
    direction      VARCHAR(10)   NULL,
    description    TEXT          NULL,
    created_at     DATETIME(6)   NOT NULL,
    updated_at     DATETIME(6)   NOT NULL,
    CONSTRAINT pk_property PRIMARY KEY (id),
    INDEX idx_property_location (lat, lng),
    INDEX idx_property_price (deposit, monthly_rent),
    INDEX idx_property_sgg_umd (sgg_code, umd_name)
);

CREATE TABLE favorite
(
    id          BINARY(16)  NOT NULL,
    property_id BINARY(16)  NOT NULL,
    user_id     BINARY(16)  NOT NULL,
    created_at  DATETIME(6) NOT NULL,
    CONSTRAINT pk_favorite PRIMARY KEY (id),
    CONSTRAINT uk_favorite_user_property UNIQUE (user_id, property_id),
    CONSTRAINT fk_favorite_property FOREIGN KEY (property_id) REFERENCES property (id) ON DELETE CASCADE,
    CONSTRAINT fk_favorite_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE TABLE property_feature
(
    id            BINARY(16)    NOT NULL,
    property_id   BINARY(16)    NOT NULL,
    category      VARCHAR(50)   NOT NULL,
    metric_code   VARCHAR(50)   NOT NULL,
    numeric_value DECIMAL(18, 6) NULL,
    text_value    VARCHAR(255)  NULL,
    unit          VARCHAR(30)   NULL,
    computed_at   DATETIME(6)   NOT NULL,
    CONSTRAINT pk_property_feature PRIMARY KEY (id),
    CONSTRAINT fk_property_feature_property FOREIGN KEY (property_id) REFERENCES property (id) ON DELETE CASCADE,
    CONSTRAINT uk_property_feature_metric UNIQUE (property_id, category, metric_code)
);

CREATE TABLE client_session
(
    id                 BINARY(16)   NOT NULL,
    session_hash_token VARCHAR(255) NOT NULL,
    expires_at         DATETIME(6)  NOT NULL,
    created_at         DATETIME(6)  NOT NULL,
    last_accessed_at   DATETIME(6)  NOT NULL,
    CONSTRAINT pk_client_session PRIMARY KEY (id),
    CONSTRAINT uk_client_session_hash UNIQUE (session_hash_token),
    INDEX idx_client_session_expires_at (expires_at)
);

CREATE TABLE recommendation
(
    id                BINARY(16)  NOT NULL,
    user_id           BINARY(16)  NULL,
    client_session_id BINARY(16)  NULL,
    status            VARCHAR(20) NOT NULL,
    requested_at      DATETIME(6) NOT NULL,
    started_at        DATETIME(6) NULL,
    completed_at      DATETIME(6) NULL,
    CONSTRAINT pk_recommendation PRIMARY KEY (id),
    CONSTRAINT fk_recommendation_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_recommendation_client_session FOREIGN KEY (client_session_id) REFERENCES client_session (id) ON DELETE CASCADE,
    CONSTRAINT chk_recommendation_owner CHECK (
        (user_id IS NOT NULL AND client_session_id IS NULL)
        OR (user_id IS NULL AND client_session_id IS NOT NULL)
    ),
    INDEX idx_recommendation_user_requested (user_id, requested_at),
    INDEX idx_recommendation_session_requested (client_session_id, requested_at)
);

CREATE TABLE recommendation_criteria
(
    id                         BINARY(16)   NOT NULL,
    recommendation_id          BINARY(16)   NOT NULL,
    workplace_name             VARCHAR(50)  NOT NULL,
    workplace_address          VARCHAR(255) NOT NULL,
    workplace_road_address     VARCHAR(255) NULL,
    workplace_latitude         DOUBLE       NOT NULL,
    workplace_longitude        DOUBLE       NOT NULL,
    transport_type             VARCHAR(20)  NOT NULL,
    max_commute_minutes        INT          NOT NULL,
    sunlight_importance        INT          NOT NULL,
    quietness_importance       INT          NOT NULL,
    safety_importance          INT          NOT NULL,
    infrastructure_importance  INT          NOT NULL,
    deposit_min                INT          NOT NULL,
    deposit_max                INT          NOT NULL,
    monthly_rent_min           INT          NOT NULL,
    monthly_rent_max           INT          NOT NULL,
    room_types                 VARCHAR(255) NOT NULL,
    CONSTRAINT pk_recommendation_criteria PRIMARY KEY (id),
    CONSTRAINT uk_recommendation_criteria_recommendation UNIQUE (recommendation_id),
    CONSTRAINT fk_recommendation_criteria_recommendation FOREIGN KEY (recommendation_id) REFERENCES recommendation (id) ON DELETE CASCADE,
    CONSTRAINT chk_recommendation_criteria_deposit CHECK (deposit_min <= deposit_max),
    CONSTRAINT chk_recommendation_criteria_rent CHECK (monthly_rent_min <= monthly_rent_max)
);

CREATE TABLE recommendation_result
(
    id                BINARY(16) NOT NULL,
    recommendation_id BINARY(16) NOT NULL,
    property_id       BINARY(16) NOT NULL,
    CONSTRAINT pk_recommendation_result PRIMARY KEY (id),
    CONSTRAINT uk_recommendation_result_property UNIQUE (recommendation_id, property_id),
    CONSTRAINT fk_recommendation_result_recommendation FOREIGN KEY (recommendation_id) REFERENCES recommendation (id) ON DELETE CASCADE,
    CONSTRAINT fk_recommendation_result_property FOREIGN KEY (property_id) REFERENCES property (id) ON DELETE CASCADE
);
