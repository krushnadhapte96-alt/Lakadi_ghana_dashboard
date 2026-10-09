-- V12: Khata (udhaar) - parties, ledger entries and entry line items

CREATE TABLE khata_parties (
    id                  BIGINT        NOT NULL AUTO_INCREMENT,
    shop_id             BIGINT        NOT NULL,
    customer_id         BIGINT        NULL,
    name                VARCHAR(150)  NOT NULL,
    type                VARCHAR(20)   NOT NULL DEFAULT 'INDIVIDUAL',
    phone               VARCHAR(20)   NOT NULL,
    address             VARCHAR(300)  NULL,
    credit_limit        DECIMAL(12,2) NULL,
    payment_terms_days  INT           NOT NULL DEFAULT 30,
    opening_balance     DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    active              TINYINT(1)    NOT NULL DEFAULT 1,
    created_at          DATETIME(6)   NOT NULL,
    updated_at          DATETIME(6)   NOT NULL,
    version             BIGINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uq_khata_party_phone (shop_id, phone),
    KEY idx_khata_party_shop (shop_id, active),
    CONSTRAINT fk_khata_party_shop FOREIGN KEY (shop_id) REFERENCES shops (id),
    CONSTRAINT fk_khata_party_customer FOREIGN KEY (customer_id) REFERENCES customers (id),
    CONSTRAINT chk_khata_party_type CHECK (type IN ('RETAILER', 'HOTEL', 'RESTAURANT', 'INDIVIDUAL'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE khata_entries (
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    shop_id      BIGINT        NOT NULL,
    party_id     BIGINT        NOT NULL,
    entry_type   VARCHAR(20)   NOT NULL,
    amount       DECIMAL(12,2) NOT NULL,
    delta        DECIMAL(12,2) NOT NULL,
    entry_date   DATE          NOT NULL,
    due_date     DATE          NULL,
    source_type  VARCHAR(10)   NULL,
    source_id    BIGINT        NULL,
    bill_no      VARCHAR(50)   NULL,
    payment_id   BIGINT        NULL,
    payment_mode VARCHAR(10)   NULL,
    note         VARCHAR(500)  NULL,
    created_by   BIGINT        NULL,
    created_at   DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_khata_entry_source (entry_type, source_type, source_id),
    KEY idx_khata_entry_party (party_id, entry_date, id),
    KEY idx_khata_entry_shop (shop_id, entry_date),
    CONSTRAINT fk_khata_entry_party FOREIGN KEY (party_id) REFERENCES khata_parties (id),
    CONSTRAINT fk_khata_entry_payment FOREIGN KEY (payment_id) REFERENCES payments (id),
    CONSTRAINT chk_khata_entry_type CHECK (entry_type IN ('CREDIT_SALE', 'PAYMENT_RECEIVED', 'ADJUSTMENT'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE khata_entry_items (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    entry_id      BIGINT        NOT NULL,
    shop_id       BIGINT        NOT NULL,
    product_id    BIGINT        NULL,
    product_name  VARCHAR(150)  NOT NULL,
    qty           DECIMAL(12,3) NOT NULL,
    unit          VARCHAR(10)   NULL,
    rate          DECIMAL(10,2) NOT NULL,
    amount        DECIMAL(12,2) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_khata_item_entry (entry_id),
    CONSTRAINT fk_khata_item_entry FOREIGN KEY (entry_id) REFERENCES khata_entries (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
