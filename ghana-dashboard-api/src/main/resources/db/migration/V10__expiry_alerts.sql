-- V10: Expiry alerts

-- Current discount in effect for a stock lot (one active override per lot).
CREATE TABLE batch_price_overrides (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    shop_id         BIGINT        NOT NULL,
    batch_stock_id  BIGINT        NOT NULL,
    discount_pct    DECIMAL(5,2)  NOT NULL,
    reason          VARCHAR(255)  NULL,
    created_by      BIGINT        NOT NULL,
    created_at      DATETIME(6)   NOT NULL,
    updated_at      DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_bpo_batch_stock (batch_stock_id),
    KEY idx_bpo_shop (shop_id),
    CONSTRAINT fk_bpo_batch_stock FOREIGN KEY (batch_stock_id) REFERENCES product_batch_stock (id) ON DELETE CASCADE,
    CONSTRAINT fk_bpo_user FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT chk_bpo_discount CHECK (discount_pct > 0 AND discount_pct <= 90)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- History of snooze / discount / write-off actions taken from the expiry alerts screen.
CREATE TABLE expiry_actions (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    shop_id           BIGINT        NOT NULL,
    batch_stock_id    BIGINT        NOT NULL,
    product_id        BIGINT        NOT NULL,
    action_type       VARCHAR(20)   NOT NULL,
    discount_pct      DECIMAL(5,2)  NULL,
    write_off_qty     DECIMAL(12,3) NULL,
    write_off_value   DECIMAL(12,2) NULL,
    snoozed_until     DATE          NULL,
    note              VARCHAR(500)  NULL,
    performed_by      BIGINT        NOT NULL,
    created_at        DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    KEY idx_ea_shop (shop_id),
    KEY idx_ea_batch_stock (batch_stock_id),
    CONSTRAINT fk_ea_batch_stock FOREIGN KEY (batch_stock_id) REFERENCES product_batch_stock (id) ON DELETE CASCADE,
    CONSTRAINT fk_ea_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT fk_ea_user FOREIGN KEY (performed_by) REFERENCES users (id),
    CONSTRAINT chk_ea_action_type CHECK (action_type IN ('SNOOZE', 'DISCOUNT', 'WRITE_OFF'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
