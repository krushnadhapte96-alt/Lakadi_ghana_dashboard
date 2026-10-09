-- V8: Production / Batch management

-- Batch statuses
-- PRESSING -> SETTLING -> READY -> BOTTLED -> SOLD_OUT

CREATE TABLE batches (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    shop_id         BIGINT        NOT NULL,
    code            VARCHAR(30)   NOT NULL,
    oil_type_id     BIGINT        NOT NULL,
    seed_type_id    BIGINT        NOT NULL,
    seed_kg         DECIMAL(12,3) NOT NULL,
    press_start     DATETIME(6)   NOT NULL,
    oil_litres      DECIMAL(12,3) NOT NULL DEFAULT 0.000,
    khali_kg        DECIMAL(12,3) NOT NULL DEFAULT 0.000,
    yield_pct       DECIMAL(5,2)  NOT NULL DEFAULT 0.00,
    settling_start  DATETIME(6)   NULL,
    ready_date      DATE          NULL,
    mfg_date        DATE          NOT NULL,
    best_before_date DATE         NOT NULL,
    status          VARCHAR(20)   NOT NULL DEFAULT 'PRESSING',
    notes           VARCHAR(1000) NULL,
    created_at      DATETIME(6)   NOT NULL,
    updated_at      DATETIME(6)   NOT NULL,
    version         BIGINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uq_batch_code (code),
    KEY idx_batch_shop (shop_id),
    KEY idx_batch_status (status),
    KEY idx_batch_oil_type (oil_type_id),
    KEY idx_batch_code (code),
    CONSTRAINT fk_batches_shop FOREIGN KEY (shop_id) REFERENCES shops (id),
    CONSTRAINT fk_batches_oil_type FOREIGN KEY (oil_type_id) REFERENCES oil_types (id),
    CONSTRAINT fk_batches_seed_type FOREIGN KEY (seed_type_id) REFERENCES seed_types (id),
    CONSTRAINT chk_batch_status CHECK (status IN ('PRESSING', 'SETTLING', 'READY', 'BOTTLED', 'SOLD_OUT')),
    CONSTRAINT chk_batch_yield CHECK (yield_pct >= 0 AND yield_pct <= 100)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Batch packaging usage (tracking packaging consumed during bottling)
CREATE TABLE batch_packaging_usage (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    batch_id        BIGINT        NOT NULL,
    packaging_item_id BIGINT      NOT NULL,
    product_id      BIGINT        NOT NULL,
    units_used      DECIMAL(12,3) NOT NULL,
    created_at      DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    KEY idx_bpu_batch (batch_id),
    KEY idx_bpu_packaging (packaging_item_id),
    CONSTRAINT fk_bpu_batch FOREIGN KEY (batch_id) REFERENCES batches (id) ON DELETE CASCADE,
    CONSTRAINT fk_bpu_packaging FOREIGN KEY (packaging_item_id) REFERENCES packaging_items (id),
    CONSTRAINT fk_bpu_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Product-packaging mapping (defines which packaging a product uses)
CREATE TABLE product_packaging (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    shop_id         BIGINT        NOT NULL,
    product_id      BIGINT        NOT NULL,
    packaging_item_id BIGINT      NOT NULL,
    units_per_pack  DECIMAL(10,3) NOT NULL DEFAULT 1.000,
    active          TINYINT(1)    NOT NULL DEFAULT 1,
    created_at      DATETIME(6)   NOT NULL,
    updated_at      DATETIME(6)   NOT NULL,
    version         BIGINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uq_prod_pack_product_packaging (product_id, packaging_item_id),
    KEY idx_pp_shop (shop_id),
    KEY idx_pp_product (product_id),
    KEY idx_pp_packaging (packaging_item_id),
    CONSTRAINT fk_pp_shop FOREIGN KEY (shop_id) REFERENCES shops (id),
    CONSTRAINT fk_pp_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE,
    CONSTRAINT fk_pp_packaging FOREIGN KEY (packaging_item_id) REFERENCES packaging_items (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Batch quality notes
CREATE TABLE batch_quality_notes (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    batch_id        BIGINT        NOT NULL,
    note            VARCHAR(1000) NOT NULL,
    checked_by      VARCHAR(100)  NULL,
    quality_level   VARCHAR(20)   NULL,
    created_at      DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    KEY idx_bqn_batch (batch_id),
    CONSTRAINT fk_bqn_batch FOREIGN KEY (batch_id) REFERENCES batches (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Add batch_id to stock_movements for FIFO batch tracking on sales
ALTER TABLE stock_movements
    ADD COLUMN batch_source_id BIGINT NULL AFTER batch_id,
    ADD CONSTRAINT fk_stock_movements_batch_source FOREIGN KEY (batch_source_id) REFERENCES batches (id),
    ADD KEY idx_stock_movement_batch_source (batch_source_id);

-- Create index on product_batch_stock for batch tracking
ALTER TABLE product_batch_stock
    ADD COLUMN batch_id_ref VARCHAR(50) NULL AFTER batch_id,
    ADD KEY idx_pbs_batch_ref (batch_id_ref);
