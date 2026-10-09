-- V16: settings — shop profile, branding and payment identity for bills.
--
-- Identity/branding live as real columns on `shops` because the bill renderer reads
-- them on every single print, and they are first-class values rather than free-form
-- preferences. Everything tunable-per-shop that is not needed on the hot path
-- (billing switches, defaults, notification toggles) reuses the `app_settings`
-- key/value table created back in V1, which has been sitting unused until now.

ALTER TABLE shops
    ADD COLUMN email        VARCHAR(191) NULL AFTER mobile,
    ADD COLUMN fssai_number VARCHAR(100) NULL AFTER gstin,
    ADD COLUMN upi_id       VARCHAR(100) NULL AFTER fssai_number,
    -- Logo lives on disk under uploads/settings/<shopId>/ and is proxied through an
    -- authenticated endpoint; it is never served as a static resource.
    ADD COLUMN logo_path    VARCHAR(500) NULL AFTER upi_id,
    ADD COLUMN logo_name    VARCHAR(255) NULL AFTER logo_path,
    ADD COLUMN logo_type    VARCHAR(100) NULL AFTER logo_name,
    -- Bank account details are AES-256-GCM encrypted (see AesCryptoService) and are
    -- never returned to a client in plaintext - only a masked display string.
    ADD COLUMN bank_details_enc TEXT NULL AFTER logo_type;

-- "My Account" is the one settings surface a staff member can reach, so the language
-- they read the UI in belongs to the user rather than the shop.
ALTER TABLE users
    ADD COLUMN preferred_language VARCHAR(10) NULL AFTER role;