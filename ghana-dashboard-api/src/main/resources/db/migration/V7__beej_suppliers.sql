-- V7: Beej (raw seed) purchasing, seed stock and suppliers

-- Seed types (global reference data, linked to oil types)
CREATE TABLE seed_types (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    oil_type_id BIGINT       NOT NULL,
    name_en     VARCHAR(50)  NOT NULL,
    name_hi     VARCHAR(50)  NOT NULL,
    active      TINYINT(1)   NOT NULL DEFAULT 1,
    created_at  DATETIME(6)  NOT NULL,
    updated_at  DATETIME(6)  NOT NULL,
    version     BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uq_seed_type_oil_type (oil_type_id),
    KEY idx_seed_type_active (active),
    CONSTRAINT fk_seed_types_oil_types FOREIGN KEY (oil_type_id) REFERENCES oil_types (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Suppliers
CREATE TABLE suppliers (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    shop_id     BIGINT       NOT NULL,
    name        VARCHAR(150) NOT NULL,
    phone       VARCHAR(20)  NULL,
    address     VARCHAR(500) NULL,
    gstin       VARCHAR(20)  NULL,
    active      TINYINT(1)   NOT NULL DEFAULT 1,
    created_at  DATETIME(6)  NOT NULL,
    updated_at  DATETIME(6)  NOT NULL,
    version     BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_supplier_shop (shop_id),
    KEY idx_supplier_active (active),
    CONSTRAINT fk_suppliers_shop FOREIGN KEY (shop_id) REFERENCES shops (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Seed purchases (goods receipt from a supplier)
CREATE TABLE seed_purchases (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    shop_id           BIGINT        NOT NULL,
    seed_type_id      BIGINT        NOT NULL,
    supplier_id       BIGINT        NOT NULL,
    qty_kg            DECIMAL(12,3) NOT NULL,
    rate_per_kg       DECIMAL(10,2) NOT NULL,
    total_amount      DECIMAL(12,2) NOT NULL,
    moisture_percent  DECIMAL(5,2)  NULL,
    grade             ENUM('A', 'B', 'C') NOT NULL DEFAULT 'A',
    purchase_date     DATE          NOT NULL,
    bill_file_name    VARCHAR(255)  NULL,
    bill_file_path    VARCHAR(500)  NULL,
    bill_file_size    BIGINT        NULL,
    bill_file_type    VARCHAR(100)  NULL,
    payment_status    ENUM('PAID', 'PARTIAL', 'UNPAID') NOT NULL DEFAULT 'UNPAID',
    amount_paid       DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    created_by        BIGINT        NOT NULL,
    created_at        DATETIME(6)   NOT NULL,
    updated_at        DATETIME(6)   NOT NULL,
    version           BIGINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_seed_purchase_shop_date (shop_id, purchase_date),
    KEY idx_seed_purchase_seed_type (seed_type_id),
    KEY idx_seed_purchase_supplier (supplier_id),
    CONSTRAINT chk_seed_purchase_qty CHECK (qty_kg > 0),
    CONSTRAINT chk_seed_purchase_rate CHECK (rate_per_kg >= 0),
    CONSTRAINT chk_seed_purchase_paid CHECK (amount_paid >= 0),
    CONSTRAINT fk_seed_purchases_shop FOREIGN KEY (shop_id) REFERENCES shops (id),
    CONSTRAINT fk_seed_purchases_seed_type FOREIGN KEY (seed_type_id) REFERENCES seed_types (id),
    CONSTRAINT fk_seed_purchases_supplier FOREIGN KEY (supplier_id) REFERENCES suppliers (id),
    CONSTRAINT fk_seed_purchases_created_by FOREIGN KEY (created_by) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Current available seed stock per shop, per seed type (moving-average cost)
CREATE TABLE seed_stock (
    id                  BIGINT        NOT NULL AUTO_INCREMENT,
    shop_id             BIGINT        NOT NULL,
    seed_type_id        BIGINT        NOT NULL,
    available_kg        DECIMAL(12,3) NOT NULL DEFAULT 0.000,
    avg_rate            DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    low_stock_threshold DECIMAL(12,3) NOT NULL DEFAULT 100.000,
    updated_at          DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_seed_stock_shop_seed_type (shop_id, seed_type_id),
    CONSTRAINT fk_seed_stock_shop FOREIGN KEY (shop_id) REFERENCES shops (id),
    CONSTRAINT fk_seed_stock_seed_type FOREIGN KEY (seed_type_id) REFERENCES seed_types (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Seed stock movement ledger
CREATE TABLE seed_stock_movements (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    shop_id        BIGINT        NOT NULL,
    seed_type_id   BIGINT        NOT NULL,
    change_qty     DECIMAL(12,3) NOT NULL,
    type           ENUM('PURCHASE_IN', 'BATCH_OUT', 'ADJUST', 'WASTE') NOT NULL,
    rate           DECIMAL(10,2) NULL,
    total_value    DECIMAL(12,2) NULL,
    reason         VARCHAR(255)  NULL,
    note           VARCHAR(500)  NULL,
    reference_type VARCHAR(50)   NULL,
    reference_id   BIGINT        NULL,
    user_id        BIGINT        NOT NULL,
    created_at     DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    KEY idx_seed_movement_shop_seed_type (shop_id, seed_type_id),
    KEY idx_seed_movement_type (type),
    KEY idx_seed_movement_reference (reference_type, reference_id),
    KEY idx_seed_movement_created (created_at),
    CONSTRAINT fk_seed_movements_shop FOREIGN KEY (shop_id) REFERENCES shops (id),
    CONSTRAINT fk_seed_movements_seed_type FOREIGN KEY (seed_type_id) REFERENCES seed_types (id),
    CONSTRAINT fk_seed_movements_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Supplier payments (also mirrored into the shared payments/cash book ledger)
CREATE TABLE supplier_payments (
    id               BIGINT        NOT NULL AUTO_INCREMENT,
    shop_id          BIGINT        NOT NULL,
    supplier_id      BIGINT        NOT NULL,
    seed_purchase_id BIGINT        NULL,
    amount           DECIMAL(12,2) NOT NULL,
    mode             ENUM('CASH', 'UPI', 'CARD', 'BANK') NOT NULL,
    payment_date     DATE          NOT NULL,
    note             TEXT          NULL,
    reference_no     VARCHAR(100)  NULL,
    created_by       BIGINT        NOT NULL,
    created_at       DATETIME(6)   NOT NULL,
    updated_at       DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    KEY idx_supplier_payment_shop (shop_id),
    KEY idx_supplier_payment_supplier (supplier_id),
    KEY idx_supplier_payment_purchase (seed_purchase_id),
    CONSTRAINT chk_supplier_payment_amount CHECK (amount > 0),
    CONSTRAINT fk_supplier_payments_shop FOREIGN KEY (shop_id) REFERENCES shops (id),
    CONSTRAINT fk_supplier_payments_supplier FOREIGN KEY (supplier_id) REFERENCES suppliers (id),
    CONSTRAINT fk_supplier_payments_purchase FOREIGN KEY (seed_purchase_id) REFERENCES seed_purchases (id),
    CONSTRAINT fk_supplier_payments_created_by FOREIGN KEY (created_by) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Seed a seed type for every existing oil type (raw material feeding that oil)
INSERT INTO seed_types (oil_type_id, name_en, name_hi, created_at, updated_at)
SELECT id, name_en, name_hi, NOW(6), NOW(6) FROM oil_types;
