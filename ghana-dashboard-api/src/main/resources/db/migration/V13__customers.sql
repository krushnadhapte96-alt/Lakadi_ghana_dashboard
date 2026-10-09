-- V13: Customer insights - notes plus purchase statistics and refill prediction

ALTER TABLE customers
    ADD COLUMN tags                  VARCHAR(255)  NULL AFTER gst_no,
    ADD COLUMN preferred_language    VARCHAR(5)    NOT NULL DEFAULT 'en' AFTER tags,
    ADD COLUMN total_orders          INT           NOT NULL DEFAULT 0 AFTER preferred_language,
    ADD COLUMN total_spent           DECIMAL(14,2) NOT NULL DEFAULT 0.00 AFTER total_orders,
    ADD COLUMN last_purchase_at      DATETIME(6)   NULL AFTER total_spent,
    ADD COLUMN avg_gap_days          DECIMAL(7,1)  NULL AFTER last_purchase_at,
    ADD COLUMN predicted_refill_date DATE          NULL AFTER avg_gap_days,
    ADD COLUMN favourite_product_id  BIGINT        NULL AFTER predicted_refill_date,
    ADD KEY idx_customers_refill (shop_id, predicted_refill_date),
    ADD KEY idx_customers_last_purchase (shop_id, last_purchase_at),
    ADD CONSTRAINT fk_customers_fav_product FOREIGN KEY (favourite_product_id) REFERENCES products (id);

CREATE TABLE customer_notes (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    shop_id     BIGINT        NOT NULL,
    customer_id BIGINT        NOT NULL,
    note        VARCHAR(1000) NOT NULL,
    created_by  BIGINT        NOT NULL,
    created_at  DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    KEY idx_customer_notes_customer (customer_id, created_at),
    CONSTRAINT fk_customer_notes_customer FOREIGN KEY (customer_id) REFERENCES customers (id) ON DELETE CASCADE,
    CONSTRAINT fk_customer_notes_user FOREIGN KEY (created_by) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Back-fill statistics for customers that already have history.
UPDATE customers c
SET c.total_orders = (SELECT COUNT(*) FROM sales s WHERE s.customer_id = c.id AND s.is_void = 0 AND s.total_amount > 0)
                   + (SELECT COUNT(*) FROM orders o WHERE o.customer_id = c.id AND o.status = 'DELIVERED'),
    c.total_spent = (SELECT COALESCE(SUM(s.total_amount), 0) FROM sales s WHERE s.customer_id = c.id AND s.is_void = 0)
                  + (SELECT COALESCE(SUM(o.total_amount), 0) FROM orders o WHERE o.customer_id = c.id AND o.status = 'DELIVERED'),
    c.last_purchase_at = (SELECT MAX(t.at) FROM (
        SELECT customer_id, created_at AS at FROM sales WHERE is_void = 0 AND total_amount > 0
        UNION ALL
        SELECT customer_id, COALESCE(actual_delivery_date, created_at) FROM orders WHERE status = 'DELIVERED') t
        WHERE t.customer_id = c.id);
