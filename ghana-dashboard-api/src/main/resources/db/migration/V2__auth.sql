-- V2: authentication — users + refresh tokens

CREATE TABLE users (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    shop_id       BIGINT        NOT NULL,
    name          VARCHAR(150)  NOT NULL,
    phone         VARCHAR(20)   NOT NULL,
    email         VARCHAR(191)  NULL,
    password      VARCHAR(255)  NOT NULL,
    role          VARCHAR(20)   NOT NULL DEFAULT 'STAFF',
    active        TINYINT(1)    NOT NULL DEFAULT 1,
    last_login_at DATETIME(6)   NULL,
    created_at    DATETIME(6)   NOT NULL,
    updated_at    DATETIME(6)   NOT NULL,
    deleted_at    DATETIME(6)   NULL,
    version       BIGINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uq_users_shop_phone (shop_id, phone),
    UNIQUE KEY uq_users_phone (phone),
    UNIQUE KEY uq_users_email (email),
    KEY idx_users_shop_role (shop_id, role),
    CONSTRAINT fk_users_shops FOREIGN KEY (shop_id) REFERENCES shops (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE refresh_tokens (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    user_id     BIGINT       NOT NULL,
    token_hash  VARCHAR(64)  NOT NULL,
    family_id   VARCHAR(36)  NOT NULL,
    expires_at  DATETIME(6)  NOT NULL,
    revoked_at  DATETIME(6)  NULL,
    replaced_by BIGINT       NULL,
    created_at  DATETIME(6)  NOT NULL,
    created_ip  VARCHAR(45)  NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_refresh_token_hash (token_hash),
    KEY idx_refresh_user (user_id),
    KEY idx_refresh_family (family_id),
    CONSTRAINT fk_refresh_users FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_refresh_replaced FOREIGN KEY (replaced_by) REFERENCES refresh_tokens (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;