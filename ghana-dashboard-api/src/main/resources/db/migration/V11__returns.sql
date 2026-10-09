-- V11: Returns & Complaints

CREATE TABLE returns (
    id                   BIGINT        NOT NULL AUTO_INCREMENT,
    shop_id              BIGINT        NOT NULL,
    return_no            VARCHAR(30)   NOT NULL,
    source_type          VARCHAR(10)   NOT NULL,
    source_id            BIGINT        NOT NULL,
    customer_id          BIGINT        NULL,
    type                 VARCHAR(20)   NOT NULL,
    status               VARCHAR(20)   NOT NULL DEFAULT 'OPEN',
    resolution           VARCHAR(20)   NULL,
    description          VARCHAR(1000) NULL,
    refund_amount        DECIMAL(12,2) NULL,
    replacement_sale_id  BIGINT        NULL,
    reported_by          BIGINT        NOT NULL,
    resolved_by          BIGINT        NULL,
    resolved_at          DATETIME(6)   NULL,
    created_at           DATETIME(6)   NOT NULL,
    updated_at           DATETIME(6)   NOT NULL,
    version              BIGINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uq_returns_no (return_no),
    KEY idx_returns_shop (shop_id),
    KEY idx_returns_status (status),
    KEY idx_returns_source (source_type, source_id),
    CONSTRAINT fk_returns_shop FOREIGN KEY (shop_id) REFERENCES shops (id),
    CONSTRAINT fk_returns_customer FOREIGN KEY (customer_id) REFERENCES customers (id),
    CONSTRAINT fk_returns_replacement_sale FOREIGN KEY (replacement_sale_id) REFERENCES sales (id),
    CONSTRAINT fk_returns_reported_by FOREIGN KEY (reported_by) REFERENCES users (id),
    CONSTRAINT fk_returns_resolved_by FOREIGN KEY (resolved_by) REFERENCES users (id),
    CONSTRAINT chk_returns_source_type CHECK (source_type IN ('SALE', 'ORDER')),
    CONSTRAINT chk_returns_type CHECK (type IN ('LEAKAGE', 'BROKEN', 'TASTE_SMELL', 'WRONG_ITEM', 'QTY_SHORT', 'OTHER')),
    CONSTRAINT chk_returns_status CHECK (status IN ('OPEN', 'IN_REVIEW', 'RESOLVED')),
    CONSTRAINT chk_returns_resolution CHECK (resolution IN ('REFUND', 'REPLACE', 'REJECT'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE return_items (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    return_id   BIGINT        NOT NULL,
    shop_id     BIGINT        NOT NULL,
    product_id  BIGINT        NOT NULL,
    batch_id    VARCHAR(100)  NULL,
    qty         DECIMAL(12,3) NOT NULL,
    unit_price  DECIMAL(10,2) NOT NULL,
    action      VARCHAR(20)   NULL,
    created_at  DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    KEY idx_return_items_return (return_id),
    KEY idx_return_items_product (product_id),
    KEY idx_return_items_batch (batch_id),
    CONSTRAINT fk_return_items_return FOREIGN KEY (return_id) REFERENCES returns (id) ON DELETE CASCADE,
    CONSTRAINT fk_return_items_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT chk_return_items_action CHECK (action IN ('RESTOCK', 'WRITE_OFF'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE return_photos (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    return_id   BIGINT        NOT NULL,
    shop_id     BIGINT        NOT NULL,
    file_name   VARCHAR(255)  NOT NULL,
    file_path   VARCHAR(500)  NOT NULL,
    file_size   BIGINT        NOT NULL,
    file_type   VARCHAR(100)  NOT NULL,
    created_at  DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    KEY idx_return_photos_return (return_id),
    CONSTRAINT fk_return_photos_return FOREIGN KEY (return_id) REFERENCES returns (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
