-- Shop Settings Entity Migration
-- Creates a typed settings table per shop, seeded from existing app_settings key/value pairs.
-- The old app_settings table is kept for backward compatibility and future extensions.

CREATE TABLE shop_settings (
    shop_id BIGINT NOT NULL PRIMARY KEY REFERENCES shops(id) ON DELETE CASCADE,
    bill_prefix VARCHAR(10) NOT NULL DEFAULT 'S',
    footer_message VARCHAR(300),
    show_batch_number BOOLEAN NOT NULL DEFAULT FALSE,
    show_upi_qr BOOLEAN NOT NULL DEFAULT TRUE,
    show_bank_details BOOLEAN NOT NULL DEFAULT FALSE,
    low_stock_threshold NUMERIC(10,3) NOT NULL DEFAULT 0,
    best_before_days INTEGER NOT NULL DEFAULT 365,
    credit_sales_allowed BOOLEAN NOT NULL DEFAULT TRUE,
    credit_period_days INTEGER NOT NULL DEFAULT 30,
    credit_limit NUMERIC(12,2) NOT NULL DEFAULT 0,
    alert_email VARCHAR(255),
    low_stock_alerts BOOLEAN NOT NULL DEFAULT TRUE,
    expiry_alerts BOOLEAN NOT NULL DEFAULT TRUE,
    licence_reminders BOOLEAN NOT NULL DEFAULT TRUE,
    payment_reminders BOOLEAN NOT NULL DEFAULT FALSE,
    daily_digest BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0
);

-- Seed one row per existing shop from app_settings (fallback to defaults where missing)
INSERT INTO shop_settings (
    shop_id,
    bill_prefix,
    footer_message,
    show_batch_number,
    show_upi_qr,
    show_bank_details,
    low_stock_threshold,
    best_before_days,
    credit_sales_allowed,
    credit_period_days,
    credit_limit,
    alert_email,
    low_stock_alerts,
    expiry_alerts,
    licence_reminders,
    payment_reminders,
    daily_digest
)
SELECT
    s.id,
    COALESCE(bill.setting_value, 'S'),
    COALESCE(footer.setting_value, ''),
    COALESCE(batch.setting_value, 'false') = 'true',
    COALESCE(qr.setting_value, 'true') = 'true',
    COALESCE(bank.setting_value, 'false') = 'true',
    CAST(COALESCE(low_stock.setting_value, '0') AS DECIMAL(10,3)),
    CAST(COALESCE(best_before.setting_value, '365') AS UNSIGNED),
    COALESCE(credit_allowed.setting_value, 'true') = 'true',
    CAST(COALESCE(credit_days.setting_value, '30') AS UNSIGNED),
    CAST(COALESCE(credit_limit.setting_value, '0') AS DECIMAL(12,2)),
    COALESCE(alert_email.setting_value, ''),
    COALESCE(low_stock_alerts.setting_value, 'true') = 'true',
    COALESCE(expiry_alerts.setting_value, 'true') = 'true',
    COALESCE(licence_alerts.setting_value, 'true') = 'true',
    COALESCE(payment_alerts.setting_value, 'false') = 'true',
    COALESCE(daily_digest.setting_value, 'false') = 'true'
FROM shops s
LEFT JOIN app_settings bill ON bill.shop_id = s.id AND bill.setting_key = 'billing.bill_prefix' AND bill.deleted_at IS NULL
LEFT JOIN app_settings footer ON footer.shop_id = s.id AND footer.setting_key = 'billing.footer_message' AND footer.deleted_at IS NULL
LEFT JOIN app_settings batch ON batch.shop_id = s.id AND batch.setting_key = 'billing.show_batch_number' AND batch.deleted_at IS NULL
LEFT JOIN app_settings qr ON qr.shop_id = s.id AND qr.setting_key = 'billing.show_upi_qr' AND qr.deleted_at IS NULL
LEFT JOIN app_settings bank ON bank.shop_id = s.id AND bank.setting_key = 'billing.show_bank_details' AND bank.deleted_at IS NULL
LEFT JOIN app_settings low_stock ON low_stock.shop_id = s.id AND low_stock.setting_key = 'defaults.low_stock_threshold' AND low_stock.deleted_at IS NULL
LEFT JOIN app_settings best_before ON best_before.shop_id = s.id AND best_before.setting_key = 'defaults.best_before_days' AND best_before.deleted_at IS NULL
LEFT JOIN app_settings credit_allowed ON credit_allowed.shop_id = s.id AND credit_allowed.setting_key = 'defaults.credit_sales_allowed' AND credit_allowed.deleted_at IS NULL
LEFT JOIN app_settings credit_days ON credit_days.shop_id = s.id AND credit_days.setting_key = 'defaults.credit_period_days' AND credit_days.deleted_at IS NULL
LEFT JOIN app_settings credit_limit ON credit_limit.shop_id = s.id AND credit_limit.setting_key = 'defaults.credit_limit' AND credit_limit.deleted_at IS NULL
LEFT JOIN app_settings alert_email ON alert_email.shop_id = s.id AND alert_email.setting_key = 'notifications.alert_email' AND alert_email.deleted_at IS NULL
LEFT JOIN app_settings low_stock_alerts ON low_stock_alerts.shop_id = s.id AND low_stock_alerts.setting_key = 'notifications.low_stock' AND low_stock_alerts.deleted_at IS NULL
LEFT JOIN app_settings expiry_alerts ON expiry_alerts.shop_id = s.id AND expiry_alerts.setting_key = 'notifications.expiry' AND expiry_alerts.deleted_at IS NULL
LEFT JOIN app_settings licence_alerts ON licence_alerts.shop_id = s.id AND licence_alerts.setting_key = 'notifications.licences' AND licence_alerts.deleted_at IS NULL
LEFT JOIN app_settings payment_alerts ON payment_alerts.shop_id = s.id AND payment_alerts.setting_key = 'notifications.payments' AND payment_alerts.deleted_at IS NULL
LEFT JOIN app_settings daily_digest ON daily_digest.shop_id = s.id AND daily_digest.setting_key = 'notifications.daily_digest' AND daily_digest.deleted_at IS NULL
WHERE s.deleted_at IS NULL;

-- Index for faster lookups (though PK on shop_id is the main access path)
CREATE INDEX idx_shop_settings_updated_at ON shop_settings(updated_at);