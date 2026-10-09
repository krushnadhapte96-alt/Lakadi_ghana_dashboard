-- V1: initial schema for ghana-dashboard
-- shops, audit_logs, app_settings (utf8mb4, InnoDB)

CREATE TABLE shops (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    shop_id       BIGINT       NULL,
    name          VARCHAR(150) NOT NULL,
    mobile        VARCHAR(20)  NOT NULL,
    address       VARCHAR(255) NULL,
    gstin         VARCHAR(20)  NULL,
    currency_code VARCHAR(3)   NOT NULL DEFAULT 'INR',
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_by    VARCHAR(100) NULL,
    created_at    DATETIME(6)  NOT NULL,
    updated_at    DATETIME(6)  NOT NULL,
    deleted_at    DATETIME(6)  NULL,
    version       BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uq_shops_name (name, deleted_at),
    KEY idx_shops_status (status),
    KEY idx_shops_shop_id (shop_id),
    CONSTRAINT fk_shops_self FOREIGN KEY (shop_id) REFERENCES shops (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE audit_logs (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    shop_id     BIGINT        NOT NULL,
    user_id     BIGINT        NULL,
    action      VARCHAR(50)   NOT NULL,
    entity_type VARCHAR(80)   NOT NULL,
    entity_id   BIGINT        NULL,
    details     TEXT          NULL,
    amount      DECIMAL(12,2) NULL,
    created_by  VARCHAR(100)  NULL,
    created_at  DATETIME(6)   NOT NULL,
    updated_at  DATETIME(6)   NOT NULL,
    deleted_at  DATETIME(6)   NULL,
    version     BIGINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_audit_shop_action (shop_id, action),
    KEY idx_audit_entity (entity_type, entity_id),
    KEY idx_audit_created (shop_id, created_at),
    CONSTRAINT fk_audit_shops FOREIGN KEY (shop_id) REFERENCES shops (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE app_settings (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    shop_id       BIGINT       NULL,
    setting_key   VARCHAR(120) NOT NULL,
    setting_value TEXT         NULL,
    description   VARCHAR(255) NULL,
    updated_by    VARCHAR(100) NULL,
    created_at    DATETIME(6)  NOT NULL,
    updated_at    DATETIME(6)  NOT NULL,
    deleted_at    DATETIME(6)  NULL,
    version       BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uq_settings_shop_key (shop_id, setting_key),
    KEY idx_settings_shop (shop_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Seed: the owner's primary shop (auth lands in Prompt 3)
INSERT INTO shops (shop_id, name, mobile, address, currency_code, status, created_by, created_at, updated_at, version)
VALUES (NULL, 'Main Shop', '0000000000', NULL, 'INR', 'ACTIVE', 'system', NOW(6), NOW(6), 0);