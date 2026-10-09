-- V6.1: Sale and Order entities use @Version (optimistic locking) but V4 never created the column.

ALTER TABLE sales  ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE orders ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
