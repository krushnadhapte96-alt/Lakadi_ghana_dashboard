-- V14: Documents (lab reports, photos, videos, certificates) and richer batch quality notes

CREATE TABLE documents (
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    shop_id      BIGINT        NOT NULL,
    doc_type     VARCHAR(20)   NOT NULL,
    title        VARCHAR(200)  NOT NULL,
    file_name    VARCHAR(255)  NOT NULL,
    file_path    VARCHAR(500)  NOT NULL,
    file_size    BIGINT        NOT NULL,
    file_type    VARCHAR(100)  NOT NULL,
    batch_id     BIGINT        NULL,
    notes        VARCHAR(1000) NULL,
    uploaded_by  BIGINT        NOT NULL,
    created_at   DATETIME(6)   NOT NULL,
    updated_at   DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    KEY idx_documents_shop_type (shop_id, doc_type),
    KEY idx_documents_batch (batch_id),
    CONSTRAINT fk_documents_shop FOREIGN KEY (shop_id) REFERENCES shops (id),
    CONSTRAINT fk_documents_batch FOREIGN KEY (batch_id) REFERENCES batches (id) ON DELETE SET NULL,
    CONSTRAINT fk_documents_user FOREIGN KEY (uploaded_by) REFERENCES users (id),
    CONSTRAINT chk_documents_type CHECK (doc_type IN
        ('LAB_REPORT', 'GHANA_PHOTO', 'GHANA_VIDEO', 'SHOP_PHOTO', 'CERTIFICATE', 'OTHER'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

ALTER TABLE batch_quality_notes
    MODIFY COLUMN note VARCHAR(1000) NULL,
    ADD COLUMN shop_id         BIGINT       NULL AFTER batch_id,
    ADD COLUMN colour          VARCHAR(40)  NULL,
    ADD COLUMN aroma_score     TINYINT      NULL,
    ADD COLUMN taste_score     TINYINT      NULL,
    ADD COLUMN colour_score    TINYINT      NULL,
    ADD COLUMN clarity         VARCHAR(20)  NULL,
    ADD COLUMN sediment_level  VARCHAR(10)  NULL,
    ADD COLUMN tested_by       VARCHAR(100) NULL,
    ADD COLUMN tested_on       DATE         NULL,
    ADD COLUMN remarks         VARCHAR(1000) NULL,
    ADD COLUMN updated_at      DATETIME(6)  NULL,
    ADD CONSTRAINT chk_quality_scores CHECK (
        (aroma_score  IS NULL OR aroma_score  BETWEEN 1 AND 5) AND
        (taste_score  IS NULL OR taste_score  BETWEEN 1 AND 5) AND
        (colour_score IS NULL OR colour_score BETWEEN 1 AND 5));

UPDATE batch_quality_notes q JOIN batches b ON b.id = q.batch_id
SET q.shop_id = b.shop_id, q.tested_by = q.checked_by, q.remarks = q.note, q.updated_at = q.created_at,
    q.tested_on = DATE(q.created_at);
