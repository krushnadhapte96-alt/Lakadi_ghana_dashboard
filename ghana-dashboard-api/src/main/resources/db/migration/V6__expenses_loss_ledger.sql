-- V6: Expenses, expense categories and loss ledger

CREATE TABLE expense_categories (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    name_en       VARCHAR(100) NOT NULL,
    name_hi       VARCHAR(100) NOT NULL,
    category_type ENUM('ELECTRICITY', 'LABOUR_SALARY', 'RENT', 'TRANSPORT', 'PACKAGING',
                       'REPAIRS', 'LAB_TESTING', 'LICENCE_FEES', 'OTHER') NOT NULL,
    description   TEXT         NULL,
    colour        VARCHAR(20)  NULL,
    is_active     TINYINT(1)   NOT NULL DEFAULT 1,
    sort_order    INT          NOT NULL DEFAULT 0,
    created_at    DATETIME(6)  NOT NULL,
    updated_at    DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_expense_category_type (category_type)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE expenses (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    shop_id        BIGINT        NOT NULL,
    category_id    BIGINT        NOT NULL,
    amount         DECIMAL(12,2) NOT NULL,
    title          VARCHAR(200)  NOT NULL,
    description    TEXT          NULL,
    bill_file_name VARCHAR(255)  NULL,
    bill_file_path VARCHAR(500)  NULL,
    bill_file_size BIGINT        NULL,
    bill_file_type VARCHAR(100)  NULL,
    expense_date   DATE          NOT NULL,
    payment_mode   VARCHAR(20)   NOT NULL DEFAULT 'CASH',
    reference_no   VARCHAR(100)  NULL,
    created_by     BIGINT        NOT NULL,
    created_at     DATETIME(6)   NOT NULL,
    updated_at     DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    KEY idx_expenses_shop_date (shop_id, expense_date),
    KEY idx_expenses_category (category_id),
    CONSTRAINT chk_expenses_amount CHECK (amount > 0),
    CONSTRAINT fk_expenses_shop FOREIGN KEY (shop_id) REFERENCES shops (id),
    CONSTRAINT fk_expenses_category FOREIGN KEY (category_id) REFERENCES expense_categories (id),
    CONSTRAINT fk_expenses_created_by FOREIGN KEY (created_by) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE loss_ledger (
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    shop_id      BIGINT        NOT NULL,
    loss_type    ENUM('EXPIRY', 'DAMAGE', 'LEAKAGE', 'RETURN_WRITE_OFF', 'OTHER') NOT NULL,
    product_id   BIGINT        NULL,
    batch_id     VARCHAR(100)  NULL,
    qty          DECIMAL(12,3) NOT NULL,
    value        DECIMAL(12,2) NOT NULL,
    reference_no VARCHAR(100)  NULL,
    description  TEXT          NULL,
    loss_date    DATE          NOT NULL,
    recorded_by  BIGINT        NOT NULL,
    created_at   DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    KEY idx_loss_shop_date (shop_id, loss_date),
    KEY idx_loss_type (loss_type),
    KEY idx_loss_product (product_id),
    CONSTRAINT chk_loss_qty CHECK (qty > 0),
    CONSTRAINT chk_loss_value CHECK (value >= 0),
    CONSTRAINT fk_loss_shop FOREIGN KEY (shop_id) REFERENCES shops (id),
    CONSTRAINT fk_loss_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT fk_loss_recorded_by FOREIGN KEY (recorded_by) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

INSERT INTO expense_categories (name_en, name_hi, category_type, description, colour, sort_order, created_at, updated_at) VALUES
('Electricity',   'बिजली',                 'ELECTRICITY',  'Electricity bills',           '#F59E0B', 1, NOW(6), NOW(6)),
('Labour/Salary', 'मजदूरी/वेतन',           'LABOUR_SALARY','Staff wages and salaries',    '#10B981', 2, NOW(6), NOW(6)),
('Rent',          'किराया',                'RENT',         'Rent payments',               '#3B82F6', 3, NOW(6), NOW(6)),
('Transport',     'परिवहन',                'TRANSPORT',    'Transportation costs',        '#F97316', 4, NOW(6), NOW(6)),
('Packaging',     'पैकेजिंग',              'PACKAGING',    'Packaging materials',         '#14B8A6', 5, NOW(6), NOW(6)),
('Repairs',       'मरम्मत',                'REPAIRS',      'Repair and maintenance',      '#EAB308', 6, NOW(6), NOW(6)),
('Lab Testing',   'प्रयोगशाला परीक्षण',    'LAB_TESTING',  'Quality testing costs',       '#8B5CF6', 7, NOW(6), NOW(6)),
('Licence Fees',  'लाइसेंस फीस',           'LICENCE_FEES', 'Government and licence fees', '#EF4444', 8, NOW(6), NOW(6)),
('Other',         'अन्य',                  'OTHER',        'Other expenses',              '#6B7280', 9, NOW(6), NOW(6));
