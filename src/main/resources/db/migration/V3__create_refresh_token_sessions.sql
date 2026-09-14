ALTER TABLE users
    MODIFY nickname VARCHAR(50) NULL;

CREATE TABLE refresh_token_session
(
    id         BINARY(16)  NOT NULL,
    user_id    BINARY(16)  NOT NULL,
    family_id  BINARY(16)  NOT NULL,
    token_hash CHAR(64)    NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    revoked_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_refresh_token_session PRIMARY KEY (id),
    CONSTRAINT uk_refresh_token_session_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_token_session_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    INDEX idx_refresh_token_session_user (user_id),
    INDEX idx_refresh_token_session_family (family_id),
    INDEX idx_refresh_token_session_expires (expires_at)
);
