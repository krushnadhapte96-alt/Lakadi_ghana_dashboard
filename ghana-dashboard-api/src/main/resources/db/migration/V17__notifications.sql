-- V17: Notification inbox and the per-user read ledger

-- One row per thing the shop should be told about. A row with `user_id` set is
-- addressed to that one person; a NULL `user_id` is a shop-wide broadcast that
-- every user of the shop sees.
--
-- `dedupe_key` is what stops a repeated sweep from filling the bell with the
-- same alert: the notification service refuses to insert a row whose key was
-- already used within `DEDUPE_WINDOW_HOURS`. It is deliberately not a UNIQUE
-- index, because the guarantee is "not within 24h", not "not ever" - an
-- unresolved alert has to be able to nag again the next day.
CREATE TABLE notifications (
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    shop_id      BIGINT        NOT NULL,
    user_id      BIGINT        NULL,
    type         VARCHAR(40)   NOT NULL,
    severity     VARCHAR(10)   NOT NULL DEFAULT 'INFO',
    title        VARCHAR(150)  NOT NULL,
    message      VARCHAR(500)  NULL,
    -- Frontend route the notification deep-links to, e.g. '/inventory?filter=low'.
    link         VARCHAR(200)  NULL,
    dedupe_key   VARCHAR(190)  NULL,
    -- Only set when the row is addressed to a single user. Broadcast rows track
    -- their readers in notification_reads instead, because one shared column
    -- would let the first person to open it mark it read for everybody.
    read_at      DATETIME(6)   NULL,
    created_at   DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    KEY idx_notifications_inbox (shop_id, user_id, created_at),
    KEY idx_notifications_type (shop_id, type, created_at),
    -- The de-dupe lookup: same shop, same kind of alert, most recent first.
    KEY idx_notifications_dedupe (shop_id, type, dedupe_key, created_at),
    CONSTRAINT fk_notifications_shop FOREIGN KEY (shop_id) REFERENCES shops (id),
    CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT chk_notifications_severity CHECK (severity IN ('INFO', 'SUCCESS', 'WARNING', 'CRITICAL')),
    CONSTRAINT chk_notifications_type CHECK (type IN
        ('LOW_STOCK', 'NEW_ORDER', 'ORDER_OVERDUE', 'EXPIRY', 'LICENCE',
         'UDHAAR_OVERDUE', 'SEED_LOW', 'SUPPLIER_DUE', 'REFILL_DUE',
         'DAY_NOT_CLOSED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Who has read a shop-wide broadcast. A row here means "this user has seen it",
-- which is what keeps the unread badge honest when two people share a shop.
CREATE TABLE notification_reads (
    notification_id  BIGINT      NOT NULL,
    user_id          BIGINT      NOT NULL,
    read_at          DATETIME(6) NOT NULL,
    PRIMARY KEY (notification_id, user_id),
    KEY idx_notification_reads_user (user_id, read_at),
    CONSTRAINT fk_notification_reads_notification FOREIGN KEY (notification_id)
        REFERENCES notifications (id) ON DELETE CASCADE,
    CONSTRAINT fk_notification_reads_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;