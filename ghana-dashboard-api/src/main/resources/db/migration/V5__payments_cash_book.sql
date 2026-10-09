-- V5: Payments and Cash Book

CREATE TABLE payments (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    shop_id     BIGINT        NOT NULL,
    direction   ENUM('IN', 'OUT') NOT NULL,
    mode        ENUM('CASH', 'UPI', 'CARD', 'BANK') NOT NULL,
    amount      DECIMAL(12,2) NOT NULL,
    source_type ENUM('SALE', 'ORDER', 'KHATA', 'KHALI_SALE', 'SUPPLIER', 'EXPENSE', 'REFUND', 'OTHER') NOT NULL,
    source_id   BIGINT        NOT NULL,
    reference_no VARCHAR(100) NULL,
    payment_date DATE         NOT NULL,
    note        TEXT          NULL,
    created_by  BIGINT        NOT NULL,
    created_at  DATETIME(6)   NOT NULL,
    updated_at  DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    KEY idx_payments_shop (shop_id),
    KEY idx_payments_source (source_type, source_id),
    KEY idx_payments_date (payment_date),
    KEY idx_payments_direction (direction),
    CONSTRAINT chk_payments_amount CHECK (amount > 0),
    CONSTRAINT fk_payments_shop FOREIGN KEY (shop_id) REFERENCES shops (id),
    CONSTRAINT fk_payments_created_by FOREIGN KEY (created_by) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE cash_book_days (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    shop_id         BIGINT        NOT NULL,
    day_date        DATE          NOT NULL,
    status          ENUM('OPEN', 'CLOSED', 'REOPENED') NOT NULL DEFAULT 'OPEN',
    opening_balance DECIMAL(12,2) NULL DEFAULT 0.00,
    total_in        DECIMAL(12,2) NULL DEFAULT 0.00,
    total_out       DECIMAL(12,2) NULL DEFAULT 0.00,
    closing_balance DECIMAL(12,2) NULL,
    closed_by       BIGINT        NULL,
    closed_at       DATETIME(6)   NULL,
    reopened_by     BIGINT        NULL,
    reopened_at     DATETIME(6)   NULL,
    notes           TEXT          NULL,
    created_at      DATETIME(6)   NOT NULL,
    updated_at      DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_cash_book_shop_date (shop_id, day_date),
    KEY idx_cash_book_status (status),
    CONSTRAINT fk_cash_book_shop FOREIGN KEY (shop_id) REFERENCES shops (id),
    CONSTRAINT fk_cash_book_closed_by FOREIGN KEY (closed_by) REFERENCES users (id),
    CONSTRAINT fk_cash_book_reopened_by FOREIGN KEY (reopened_by) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
