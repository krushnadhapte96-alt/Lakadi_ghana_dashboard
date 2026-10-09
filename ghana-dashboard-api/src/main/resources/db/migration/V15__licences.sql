-- V15: Business licences, their renewal history and the reminder ledger

-- One row per licence a shop holds. `status` is derived from `expiry_date` by
-- the daily sweep (LicenceScheduler), never set by hand.
CREATE TABLE licences (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    shop_id         BIGINT        NOT NULL,
    licence_number  VARCHAR(50)   NOT NULL,
    type            VARCHAR(30)   NOT NULL,
    issuer          VARCHAR(120)  NULL,
    issue_date      DATE          NOT NULL,
    expiry_date     DATE          NOT NULL,
    -- Printed on bills, so FSSAI and GST can each carry their registration number.
    fssai_gstin     VARCHAR(100)  NULL,
    document_path   VARCHAR(500)  NULL,
    document_name   VARCHAR(255)  NULL,
    document_type   VARCHAR(100)  NULL,
    status          VARCHAR(20)   NOT NULL,
    notes           VARCHAR(500)  NULL,
    created_by      BIGINT        NULL,
    created_at      DATETIME(6)   NOT NULL,
    updated_at      DATETIME(6)   NOT NULL,
    deleted_at      DATETIME(6)   NULL,
    -- The number must be unique among *live* licences only. MySQL treats NULLs
    -- as distinct in a UNIQUE index, so soft-deleting a row frees its number
    -- for re-adding the same licence later.
    live_number     VARCHAR(50)   GENERATED ALWAYS AS
                                    (IF(deleted_at IS NULL, licence_number, NULL)) STORED,
    PRIMARY KEY (id),
    KEY idx_licences_shop (shop_id),
    -- Covers the sweep (all live rows) and the summary roll-up.
    KEY idx_licences_status_expiry (shop_id, status, expiry_date),
    UNIQUE KEY uq_licences_shop_number (shop_id, live_number),
    CONSTRAINT fk_licences_shop FOREIGN KEY (shop_id) REFERENCES shops (id),
    CONSTRAINT fk_licences_user FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT chk_licences_type CHECK (type IN
        ('FSSAI', 'GST', 'SHOP_ESTABLISHMENT', 'TRADE',
         'WEIGHING_SCALE_STAMPING', 'FIRE_NOC', 'OTHER')),
    CONSTRAINT chk_licences_status CHECK (status IN ('ACTIVE', 'EXPIRING_SOON', 'EXPIRED')),
    CONSTRAINT chk_licences_dates CHECK (expiry_date >= issue_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Append-only: renewing appends a row and moves the parent forward, so the
-- full history of a licence survives every renewal.
CREATE TABLE licence_renewals (
    id                   BIGINT        NOT NULL AUTO_INCREMENT,
    shop_id              BIGINT        NOT NULL,
    licence_id           BIGINT        NOT NULL,
    previous_number      VARCHAR(50)   NULL,
    previous_expiry_date DATE          NOT NULL,
    new_number           VARCHAR(50)   NULL,
    issue_date           DATE          NOT NULL,
    expiry_date          DATE          NOT NULL,
    document_path        VARCHAR(500)  NULL,
    document_name        VARCHAR(255)  NULL,
    document_type        VARCHAR(100)  NULL,
    notes                VARCHAR(500)  NULL,
    renewed_by           BIGINT        NULL,
    created_at           DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    KEY idx_lr_shop (shop_id),
    KEY idx_lr_licence (licence_id, created_at),
    CONSTRAINT fk_lr_licence FOREIGN KEY (licence_id) REFERENCES licences (id) ON DELETE CASCADE,
    CONSTRAINT fk_lr_shop FOREIGN KEY (shop_id) REFERENCES shops (id),
    CONSTRAINT fk_lr_user FOREIGN KEY (renewed_by) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT chk_lr_dates CHECK (expiry_date >= issue_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Reminder ledger. The unique key makes each 60/30/7 threshold fire at most once
-- per licence, so a re-run of the daily sweep cannot spam the owner.
CREATE TABLE licence_reminders (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    shop_id     BIGINT        NOT NULL,
    licence_id  BIGINT        NOT NULL,
    days_left   INT           NOT NULL,
    channel     VARCHAR(20)   NOT NULL,
    sent_at     DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_licence_reminder (licence_id, days_left),
    KEY idx_lrem_shop (shop_id, sent_at),
    CONSTRAINT fk_lrem_licence FOREIGN KEY (licence_id) REFERENCES licences (id) ON DELETE CASCADE,
    CONSTRAINT fk_lrem_shop FOREIGN KEY (shop_id) REFERENCES shops (id),
    CONSTRAINT chk_lrem_channel CHECK (channel IN ('LOG', 'MAIL'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;