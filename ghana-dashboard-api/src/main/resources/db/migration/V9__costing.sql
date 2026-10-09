-- V9: Costing

-- Seed cost is locked in at batch creation time (seed_kg x the seed's weighted-avg
-- rate at that moment), so later purchases don't retroactively change a batch's cost.
ALTER TABLE batches
    ADD COLUMN seed_cost DECIMAL(12,2) NOT NULL DEFAULT 0.00 AFTER seed_kg;

-- One row per shop: default overhead assumptions used to auto-compute batch costs.
CREATE TABLE cost_defaults (
    id                  BIGINT        NOT NULL AUTO_INCREMENT,
    shop_id             BIGINT        NOT NULL,
    electricity_mode    VARCHAR(10)   NOT NULL DEFAULT 'PER_BATCH',
    electricity_value   DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    labour_mode         VARCHAR(10)   NOT NULL DEFAULT 'PER_BATCH',
    labour_value        DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    other_mode          VARCHAR(10)   NOT NULL DEFAULT 'PER_BATCH',
    other_value         DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    khali_value_per_kg  DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    target_margin_pct   DECIMAL(5,2)  NOT NULL DEFAULT 30.00,
    created_at          DATETIME(6)   NOT NULL,
    updated_at          DATETIME(6)   NOT NULL,
    version             BIGINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uq_cost_defaults_shop (shop_id),
    CONSTRAINT fk_cost_defaults_shop FOREIGN KEY (shop_id) REFERENCES shops (id),
    CONSTRAINT chk_cost_defaults_electricity_mode CHECK (electricity_mode IN ('PER_BATCH', 'PER_KG')),
    CONSTRAINT chk_cost_defaults_labour_mode CHECK (labour_mode IN ('PER_BATCH', 'PER_KG')),
    CONSTRAINT chk_cost_defaults_other_mode CHECK (other_mode IN ('PER_BATCH', 'PER_KG'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- One row per batch, auto-computed on output/bottle and recalculable on demand.
CREATE TABLE batch_costs (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    batch_id          BIGINT        NOT NULL,
    shop_id           BIGINT        NOT NULL,
    seed_cost         DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    electricity_cost  DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    labour_cost       DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    other_cost        DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    khali_credit      DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    total_cost        DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    cost_per_litre    DECIMAL(12,4) NOT NULL DEFAULT 0.0000,
    manual_override   TINYINT(1)    NOT NULL DEFAULT 0,
    computed_at        DATETIME(6)   NOT NULL,
    created_at        DATETIME(6)   NOT NULL,
    updated_at        DATETIME(6)   NOT NULL,
    version           BIGINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uq_batch_costs_batch (batch_id),
    KEY idx_batch_costs_shop (shop_id),
    CONSTRAINT fk_batch_costs_batch FOREIGN KEY (batch_id) REFERENCES batches (id) ON DELETE CASCADE,
    CONSTRAINT fk_batch_costs_shop FOREIGN KEY (shop_id) REFERENCES shops (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
