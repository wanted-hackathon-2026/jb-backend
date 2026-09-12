CREATE TABLE users
(
    id          BINARY(16)   NOT NULL,
    provider    VARCHAR(20)  NOT NULL,
    provider_id VARCHAR(255) NOT NULL,
    email       VARCHAR(255) NOT NULL,
    nickname    VARCHAR(50)  NOT NULL,
    role        VARCHAR(20)  NOT NULL,
    created_at  DATETIME(6)  NOT NULL,
    updated_at  DATETIME(6)  NOT NULL,
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uk_users_provider_provider_id UNIQUE (provider, provider_id),
    INDEX idx_users_email (email)
);
