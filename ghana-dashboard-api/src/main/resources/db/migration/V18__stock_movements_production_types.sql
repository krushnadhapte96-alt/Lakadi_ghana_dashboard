-- Production flow introduced batch output / bottling / seed-return stock movements, but the
-- stock_movements.type ENUM (V3) was never widened, so recording output from a batch failed with
-- "Data truncated for column 'type'".
ALTER TABLE stock_movements
    MODIFY COLUMN type ENUM('BATCH_IN', 'OPENING', 'SALE_OUT', 'ORDER_RESERVE', 'ORDER_RELEASE',
                           'RETURN_IN', 'ADJUST_IN', 'ADJUST_OUT', 'WRITE_OFF', 'KHATA_OUT',
                           'BATCH_OUTPUT', 'BATCH_BOTTLE', 'BATCH_RETURN_SEED') NOT NULL;