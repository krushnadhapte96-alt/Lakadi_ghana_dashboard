-- V3: Products, Packaging and Inventory

-- Oil types table
CREATE TABLE oil_types (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    name_en     VARCHAR(50)  NOT NULL,
    name_hi     VARCHAR(50)  NOT NULL,
    density     DECIMAL(5,4) NOT NULL DEFAULT 0.91,
    best_before_days INT      NOT NULL DEFAULT 365,
    colour_tag  VARCHAR(20)  NOT NULL DEFAULT 'mustard',
    active      TINYINT(1)   NOT NULL DEFAULT 1,
    created_at  DATETIME(6)  NOT NULL,
    updated_at  DATETIME(6)  NOT NULL,
    version     BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uq_oil_type_name (name_en),
    KEY idx_oil_type_active (active)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Products table
CREATE TABLE products (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    shop_id             BIGINT       NOT NULL,
    oil_type_id         BIGINT       NULL,
    name                VARCHAR(150) NOT NULL,
    sku                 VARCHAR(50)  NOT NULL,
    pack_size           DECIMAL(10,3) NOT NULL DEFAULT 1.000,
    unit                ENUM('L', 'ML', 'KG') NOT NULL DEFAULT 'L',
    container           ENUM('glass', 'plastic', 'tin', 'bag') NOT NULL DEFAULT 'plastic',
    selling_price       DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    mrp                 DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    cost_price          DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    low_stock_threshold DECIMAL(10,3) NOT NULL DEFAULT 0.000,
    active              TINYINT(1)   NOT NULL DEFAULT 1,
    is_khali            TINYINT(1)   NOT NULL DEFAULT 0,
    created_at          DATETIME(6)  NOT NULL,
    updated_at          DATETIME(6)  NOT NULL,
    version             BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uq_product_sku (shop_id, sku),
    KEY idx_product_shop (shop_id),
    KEY idx_product_oil_type (oil_type_id),
    KEY idx_product_active (active),
    KEY idx_product_khali (is_khali),
    CONSTRAINT fk_products_shops FOREIGN KEY (shop_id) REFERENCES shops (id),
    CONSTRAINT fk_products_oil_types FOREIGN KEY (oil_type_id) REFERENCES oil_types (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Packaging items table
CREATE TABLE packaging_items (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    shop_id         BIGINT       NOT NULL,
    name            VARCHAR(100) NOT NULL,
    type            ENUM('bottle', 'cap', 'label', 'tin', 'bag', 'carton') NOT NULL,
    size            VARCHAR(50)  NOT NULL,
    stock_qty       DECIMAL(12,3) NOT NULL DEFAULT 0.000,
    cost_per_unit   DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    low_threshold   DECIMAL(12,3) NOT NULL DEFAULT 0.000,
    active          TINYINT(1)   NOT NULL DEFAULT 1,
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    version         BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uq_packaging_name_type (shop_id, name, type),
    KEY idx_packaging_shop (shop_id),
    KEY idx_packaging_active (active),
    CONSTRAINT fk_packaging_shops FOREIGN KEY (shop_id) REFERENCES shops (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Packaging stock movements table
CREATE TABLE packaging_stock_movements (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    shop_id     BIGINT       NOT NULL,
    item_id     BIGINT       NOT NULL,
    change_qty  DECIMAL(12,3) NOT NULL,
    type        ENUM('PURCHASE', 'CONSUME', 'ADJUST_IN', 'ADJUST_OUT', 'OPENING') NOT NULL,
    unit_cost   DECIMAL(10,2) NULL,
    total_cost  DECIMAL(12,2) NULL,
    reason      VARCHAR(255) NULL,
    note        VARCHAR(500) NULL,
    reference_type VARCHAR(50) NULL,
    reference_id BIGINT       NULL,
    user_id     BIGINT       NOT NULL,
    created_at  DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    KEY idx_packaging_movement_item (item_id),
    KEY idx_packaging_movement_shop (shop_id),
    KEY idx_packaging_movement_user (user_id),
    KEY idx_packaging_movement_created (created_at),
    CONSTRAINT fk_packaging_movements_shops FOREIGN KEY (shop_id) REFERENCES shops (id),
    CONSTRAINT fk_packaging_movements_items FOREIGN KEY (item_id) REFERENCES packaging_items (id),
    CONSTRAINT fk_packaging_movements_users FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Product batch stock table
CREATE TABLE product_batch_stock (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    shop_id         BIGINT       NOT NULL,
    product_id      BIGINT       NOT NULL,
    batch_id        VARCHAR(100) NULL,
    qty_available    DECIMAL(12,3) NOT NULL DEFAULT 0.000,
    qty_reserved    DECIMAL(12,3) NOT NULL DEFAULT 0.000,
    cost_price      DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    received_date   DATE         NULL,
    expiry_date     DATE         NULL,
    best_before_date DATE       NULL,
    active          TINYINT(1)   NOT NULL DEFAULT 1,
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    version         BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uq_batch_shop_product_batch (shop_id, product_id, batch_id),
    KEY idx_batch_product (product_id),
    KEY idx_batch_shop (shop_id),
    KEY idx_batch_expiry (expiry_date),
    CONSTRAINT fk_batch_shops FOREIGN KEY (shop_id) REFERENCES shops (id),
    CONSTRAINT fk_batch_products FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Stock movements table
CREATE TABLE stock_movements (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    shop_id         BIGINT       NOT NULL,
    product_id      BIGINT       NOT NULL,
    batch_id        VARCHAR(100) NULL,
    change_qty      DECIMAL(12,3) NOT NULL,
    type            ENUM('BATCH_IN', 'OPENING', 'SALE_OUT', 'ORDER_RESERVE', 'ORDER_RELEASE', 'RETURN_IN', 'ADJUST_IN', 'ADJUST_OUT', 'WRITE_OFF', 'KHATA_OUT') NOT NULL,
    unit_price      DECIMAL(10,2) NULL,
    total_value     DECIMAL(12,2) NULL,
    reason          VARCHAR(255) NULL,
    note            VARCHAR(500) NULL,
    reference_type  VARCHAR(50) NULL,
    reference_id    BIGINT       NULL,
    user_id         BIGINT       NOT NULL,
    created_at      DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    KEY idx_stock_movement_product (product_id),
    KEY idx_stock_movement_batch (batch_id),
    KEY idx_stock_movement_shop (shop_id),
    KEY idx_stock_movement_user (user_id),
    KEY idx_stock_movement_type (type),
    KEY idx_stock_movement_reference (reference_type, reference_id),
    KEY idx_stock_movement_created (created_at),
    CONSTRAINT fk_stock_movements_shops FOREIGN KEY (shop_id) REFERENCES shops (id),
    CONSTRAINT fk_stock_movements_products FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE,
    CONSTRAINT fk_stock_movements_users FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Insert default oil types (seed data)
INSERT INTO oil_types (name_en, name_hi, density, best_before_days, colour_tag, created_at, updated_at) VALUES
('Mustard', 'सरसों', 0.91, 365, 'mustard', NOW(6), NOW(6)),
('Groundnut', 'मूंगफली', 0.91, 365, 'gold', NOW(6), NOW(6)),
('Sesame', 'तिल', 0.92, 365, 'gold', NOW(6), NOW(6)),
('Coconut', 'नारियल', 0.92, 365, 'white', NOW(6), NOW(6)),
('Flaxseed', 'अलसी', 0.93, 365, 'gold', NOW(6), NOW(6)),
('Sunflower', 'सूरजमुखी', 0.92, 365, 'gold', NOW(6), NOW(6)),
('Palm', 'पाल्म', 0.91, 365, 'red', NOW(6), NOW(6));
