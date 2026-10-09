-- Dummy ACTIVITY data so every screen has something to show:
-- Sales, Orders, Payments, Cash book, Khata, Expenses, Losses, Beej (seed) suppliers,
-- Production batches + costing, Packaging, Expiry alerts, Returns, Licences.
--
-- NOT a Flyway migration. Run manually on a DEV database:
--   1. Start the app once (Flyway creates tables) and REGISTER/LOGIN once, so a user exists.
--   2. Run pune_dummy_data.sql first (products, customers).
--   3. mysql -u <user> -p <db> < src/main/resources/dummy/pune_dummy_activity.sql
-- Run it ONCE (rows are not de-duplicated). All demo rows use a DEMO- prefix so they are easy to find.

SET @shop := IFNULL(@shop, 1);
SET @uid  := (SELECT id FROM users WHERE shop_id = @shop ORDER BY id LIMIT 1);

-- ---------------------------------------------------------------- SALES (counter bills)
CREATE TEMPORARY TABLE demo_sales (bill VARCHAR(20), dago INT, phone VARCHAR(20), sku VARCHAR(50), qty DECIMAL(10,3), mode VARCHAR(10));
INSERT INTO demo_sales VALUES
('DEMO-S01', 13, '9890100001', 'MUS-1L',   4, 'CASH'),
('DEMO-S02', 12, '9890100002', 'GNUT-5L',  1, 'UPI'),
('DEMO-S03', 11, NULL,         'SES-500',  2, 'CASH'),
('DEMO-S04', 10, '9890100003', 'SUN-1L',   6, 'CARD'),
('DEMO-S05',  9, '9890100006', 'MUS-5L',   3, 'UDHAAR'),
('DEMO-S06',  8, '9890100004', 'COC-500',  2, 'UPI'),
('DEMO-S07',  6, NULL,         'GNUT-1L',  5, 'CASH'),
('DEMO-S08',  5, '9890100005', 'MUS-1L',   2, 'CASH'),
('DEMO-S09',  4, '9890100006', 'GNUT-5L',  2, 'UDHAAR'),
('DEMO-S10',  3, '9890100007', 'FLX-250',  3, 'UPI'),
('DEMO-S11',  2, '9890100008', 'SES-1L',   2, 'CASH'),
('DEMO-S12',  1, '9890100001', 'MUS-5L',   1, 'UPI'),
('DEMO-S13',  0, NULL,         'MUS-1L',   3, 'CASH'),
('DEMO-S14',  0, '9890100002', 'GNUT-1L',  2, 'UPI');

INSERT INTO sales (shop_id, bill_no, customer_id, user_id, subtotal, discount, discount_type, total_amount, paid_amount,
                   payment_mode, payment_status, active, created_at, updated_at)
SELECT @shop, d.bill, c.id, @uid, p.selling_price * d.qty, 0, 'AMOUNT', p.selling_price * d.qty,
       IF(d.mode = 'UDHAAR', 0, p.selling_price * d.qty), d.mode, IF(d.mode = 'UDHAAR', 'UNPAID', 'PAID'), 1,
       TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL d.dago DAY), '11:30:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL d.dago DAY), '11:30:00')
FROM demo_sales d
JOIN products p ON p.shop_id = @shop AND p.sku = d.sku
LEFT JOIN customers c ON c.shop_id = @shop AND c.phone = d.phone;

INSERT INTO sale_items (sale_id, shop_id, product_id, batch_id, sku, product_name, qty, unit, rate, amount, cost_price, profit, created_at)
SELECT s.id, @shop, p.id, NULL, p.sku, p.name, d.qty, p.unit, p.selling_price, p.selling_price * d.qty,
       p.cost_price, (p.selling_price - p.cost_price) * d.qty, s.created_at
FROM demo_sales d
JOIN sales s ON s.shop_id = @shop AND s.bill_no = d.bill
JOIN products p ON p.shop_id = @shop AND p.sku = d.sku;

-- money received for paid sales
INSERT INTO payments (shop_id, direction, mode, amount, source_type, source_id, reference_no, payment_date, note, created_by, created_at, updated_at)
SELECT @shop, 'IN', s.payment_mode, s.total_amount, 'SALE', s.id, IF(s.payment_mode = 'UPI', CONCAT('UPI', 700000 + s.id), NULL),
       DATE(s.created_at), CONCAT('Bill ', s.bill_no), @uid, s.created_at, s.created_at
FROM sales s WHERE s.shop_id = @shop AND s.bill_no LIKE 'DEMO-S%' AND s.payment_mode IN ('CASH', 'UPI', 'CARD');
DROP TEMPORARY TABLE demo_sales;

-- ---------------------------------------------------------------- DELIVERY ORDERS
CREATE TEMPORARY TABLE demo_orders (bill VARCHAR(20), dago INT, phone VARCHAR(20), sku VARCHAR(50), qty DECIMAL(10,3), st VARCHAR(20), mode VARCHAR(10));
INSERT INTO demo_orders VALUES
('DEMO-O01', 7, '9890100001', 'MUS-5L',  2, 'DELIVERED',        'UPI'),
('DEMO-O02', 2, '9890100003', 'GNUT-5L', 1, 'OUT_FOR_DELIVERY', 'COD'),
('DEMO-O03', 1, '9890100004', 'SES-1L',  2, 'CONFIRMED',        'UPI'),
('DEMO-O04', 0, '9890100007', 'MUS-1L',  6, 'NEW',              'COD');

INSERT INTO orders (shop_id, bill_no, customer_id, address_id, user_id, subtotal, delivery_charge, discount, discount_type, total_amount,
                    paid_amount, payment_mode, payment_status, status, expected_delivery_date, actual_delivery_date, active, created_at, updated_at)
SELECT @shop, o.bill, c.id, a.id, @uid, p.selling_price * o.qty, 30, 0, 'AMOUNT', p.selling_price * o.qty + 30,
       IF(o.st = 'DELIVERED', p.selling_price * o.qty + 30, 0), o.mode, IF(o.st = 'DELIVERED', 'PAID', 'UNPAID'), o.st,
       DATE_ADD(CURDATE(), INTERVAL IF(o.st IN ('NEW', 'CONFIRMED'), 1, 0) DAY),
       IF(o.st = 'DELIVERED', DATE_SUB(CURDATE(), INTERVAL o.dago - 1 DAY), NULL), 1,
       TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL o.dago DAY), '09:15:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL o.dago DAY), '09:15:00')
FROM demo_orders o
JOIN products p ON p.shop_id = @shop AND p.sku = o.sku
JOIN customers c ON c.shop_id = @shop AND c.phone = o.phone
JOIN customer_addresses a ON a.customer_id = c.id AND a.is_default = 1;

INSERT INTO order_items (order_id, shop_id, product_id, sku, product_name, qty, unit, rate, amount, cost_price, profit, created_at)
SELECT ord.id, @shop, p.id, p.sku, p.name, o.qty, p.unit, p.selling_price, p.selling_price * o.qty, p.cost_price,
       (p.selling_price - p.cost_price) * o.qty, ord.created_at
FROM demo_orders o
JOIN orders ord ON ord.shop_id = @shop AND ord.bill_no = o.bill
JOIN products p ON p.shop_id = @shop AND p.sku = o.sku;

INSERT INTO order_status_history (order_id, shop_id, old_status, new_status, changed_by, notes, created_at)
SELECT ord.id, @shop, NULL, ord.status, @uid, 'Demo order', ord.created_at FROM orders ord WHERE ord.shop_id = @shop AND ord.bill_no LIKE 'DEMO-O%';

INSERT INTO payments (shop_id, direction, mode, amount, source_type, source_id, reference_no, payment_date, note, created_by, created_at, updated_at)
SELECT @shop, 'IN', 'UPI', ord.total_amount, 'ORDER', ord.id, CONCAT('UPI', 800000 + ord.id), ord.actual_delivery_date,
       CONCAT('Order ', ord.bill_no), @uid, ord.created_at, ord.created_at
FROM orders ord WHERE ord.shop_id = @shop AND ord.bill_no LIKE 'DEMO-O%' AND ord.status = 'DELIVERED';
DROP TEMPORARY TABLE demo_orders;

-- ---------------------------------------------------------------- KHATA (udhaar ledger)
INSERT INTO khata_parties (shop_id, customer_id, name, type, phone, address, credit_limit, payment_terms_days, opening_balance, active, created_at, updated_at)
SELECT @shop, c.id, k.name, k.type, k.phone, k.addr, k.lim, k.terms, 0, 1, NOW(6), NOW(6)
FROM (
    SELECT 'Shree Annapurna Mess' name, 'HOTEL' type, '9890100006' phone, 'Swargate, Pune'          addr, 25000.00 lim, 15 terms UNION ALL
    SELECT 'Hotel Surabhi',            'RESTAURANT',  '9890100020',       'Kothrud, Pune',                 40000.00,      30       UNION ALL
    SELECT 'Patil General Store',      'RETAILER',    '9890100021',       'Katraj, Pune',                  15000.00,      30
) k LEFT JOIN customers c ON c.shop_id = @shop AND c.phone = k.phone;

INSERT INTO khata_entries (shop_id, party_id, entry_type, amount, delta, entry_date, due_date, bill_no, payment_mode, note, created_by, created_at)
SELECT @shop, p.id, e.t, e.amt, IF(e.t = 'PAYMENT_RECEIVED', -e.amt, e.amt),
       DATE_SUB(CURDATE(), INTERVAL e.dago DAY),
       IF(e.t = 'CREDIT_SALE', DATE_ADD(DATE_SUB(CURDATE(), INTERVAL e.dago DAY), INTERVAL p.payment_terms_days DAY), NULL),
       e.bill, IF(e.t = 'PAYMENT_RECEIVED', 'UPI', NULL), e.note, @uid, TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL e.dago DAY), '12:00:00')
FROM (
    SELECT '9890100006' phone, 'CREDIT_SALE' t, 3060.00 amt,  9 dago, 'DEMO-S05' bill, 'Mustard 5L x3'    note UNION ALL
    SELECT '9890100006', 'CREDIT_SALE',        2360.00,       4,      'DEMO-S09',      'Groundnut 5L x2'        UNION ALL
    SELECT '9890100006', 'PAYMENT_RECEIVED',   2000.00,       2,      NULL,            'Part payment'           UNION ALL
    SELECT '9890100020', 'CREDIT_SALE',        9500.00,      20,      NULL,            'Monthly oil supply'     UNION ALL
    SELECT '9890100020', 'PAYMENT_RECEIVED',   4000.00,       5,      NULL,            'Cheque cleared'         UNION ALL
    SELECT '9890100021', 'CREDIT_SALE',        6200.00,      35,      NULL,            'Old balance - overdue'
) e JOIN khata_parties p ON p.shop_id = @shop AND p.phone = e.phone;

-- ---------------------------------------------------------------- EXPENSES
INSERT INTO expenses (shop_id, category_id, amount, title, description, expense_date, payment_mode, reference_no, created_by, created_at, updated_at)
SELECT @shop, ec.id, x.amt, x.title, x.descr, DATE_SUB(CURDATE(), INTERVAL x.dago DAY), x.mode, x.ref, @uid, NOW(6), NOW(6)
FROM (
    SELECT 'ELECTRICITY' t, 4850.00 amt, 'MSEDCL bill' title,           'Monthly electricity - ghana motor' descr, 12 dago, 'UPI'  mode, 'MSEB-88231' ref UNION ALL
    SELECT 'LABOUR_SALARY',  18000.00,   'Staff wages',                 '2 workers + 1 helper',                     10,      'CASH',      NULL            UNION ALL
    SELECT 'RENT',           12000.00,   'Shop rent - Gate 2',          'Market Yard shop rent',                    9,       'BANK',      'NEFT-5521'     UNION ALL
    SELECT 'TRANSPORT',       1800.00,   'Tempo - seed delivery',       'Seeds from Latur',                         7,       'CASH',      NULL            UNION ALL
    SELECT 'PACKAGING',       3200.00,   'Bottles and caps',            '500 bottles + caps',                       6,       'UPI',       'UPI-4410'      UNION ALL
    SELECT 'REPAIRS',          950.00,   'Ghana wooden pestle repair',  'Carpenter',                                4,       'CASH',      NULL            UNION ALL
    SELECT 'LAB_TESTING',     1500.00,   'Oil purity test',             'Pune lab - mustard batch',                 3,       'UPI',       'LAB-221'       UNION ALL
    SELECT 'LICENCE_FEES',    2000.00,   'FSSAI renewal fee',           'Annual fee',                               2,       'BANK',      'FSSAI-7788'
) x JOIN expense_categories ec ON ec.category_type = x.t;

INSERT INTO payments (shop_id, direction, mode, amount, source_type, source_id, payment_date, note, created_by, created_at, updated_at)
SELECT @shop, 'OUT', IF(e.payment_mode IN ('CASH', 'UPI', 'CARD', 'BANK'), e.payment_mode, 'CASH'), e.amount, 'EXPENSE', e.id,
       e.expense_date, e.title, @uid, NOW(6), NOW(6)
FROM expenses e WHERE e.shop_id = @shop AND e.created_by = @uid AND e.title IN
 ('MSEDCL bill', 'Staff wages', 'Shop rent - Gate 2', 'Tempo - seed delivery', 'Bottles and caps', 'Ghana wooden pestle repair', 'Oil purity test', 'FSSAI renewal fee');

-- ---------------------------------------------------------------- BEEJ: suppliers, purchases, stock
INSERT INTO suppliers (shop_id, name, phone, address, gstin, active, created_at, updated_at) VALUES
(@shop, 'Latur Krushi Seeds',        '9422001001', 'APMC Yard, Latur',          '27ABCDE1234F1Z5', 1, NOW(6), NOW(6)),
(@shop, 'Solapur Groundnut Traders', '9422001002', 'Market Yard, Solapur',      NULL,              1, NOW(6), NOW(6)),
(@shop, 'Nashik Til Bhandar',        '9422001003', 'Panchavati, Nashik',        NULL,              1, NOW(6), NOW(6));

INSERT INTO seed_purchases (shop_id, seed_type_id, supplier_id, qty_kg, rate_per_kg, total_amount, moisture_percent, grade,
                            purchase_date, payment_status, amount_paid, created_by, created_at, updated_at)
SELECT @shop, st.id, s.id, q.kg, q.rate, q.kg * q.rate, q.moist, q.grade, DATE_SUB(CURDATE(), INTERVAL q.dago DAY), q.pst, q.paid, @uid, NOW(6), NOW(6)
FROM (
    SELECT 'Mustard' seed, 'Latur Krushi Seeds' sup, 500.000 kg, 62.00 rate, 6.50 moist, 'A' grade, 25 dago, 'PAID'    pst, 31000.00 paid UNION ALL
    SELECT 'Mustard',      'Latur Krushi Seeds',      300.000,    64.00,     7.20,       'B',       8,       'PARTIAL',      10000.00      UNION ALL
    SELECT 'Groundnut',    'Solapur Groundnut Traders',400.000,   95.00,     7.00,       'A',       15,      'UNPAID',       0.00          UNION ALL
    SELECT 'Sesame',       'Nashik Til Bhandar',      150.000,    140.00,    5.80,       'A',       12,      'PAID',         21000.00
) q
JOIN seed_types st ON st.name_en = q.seed
JOIN suppliers s ON s.shop_id = @shop AND s.name = q.sup;

INSERT IGNORE INTO seed_stock (shop_id, seed_type_id, available_kg, avg_rate, low_stock_threshold, updated_at)
SELECT @shop, st.id, v.kg, v.rate, v.low, NOW(6)
FROM (
    SELECT 'Mustard' n, 420.000 kg, 62.75 rate, 150.000 low UNION ALL
    SELECT 'Groundnut',  310.000,    95.00,     150.000      UNION ALL
    SELECT 'Sesame',      60.000,    140.00,    100.000
) v JOIN seed_types st ON st.name_en = v.n;

INSERT INTO seed_stock_movements (shop_id, seed_type_id, change_qty, type, rate, total_value, reason, user_id, created_at)
SELECT @shop, sp.seed_type_id, sp.qty_kg, 'PURCHASE_IN', sp.rate_per_kg, sp.total_amount, 'Seed purchase', @uid, sp.created_at
FROM seed_purchases sp WHERE sp.shop_id = @shop AND sp.created_by = @uid;

INSERT INTO supplier_payments (shop_id, supplier_id, seed_purchase_id, amount, mode, payment_date, note, created_by, created_at, updated_at)
SELECT @shop, sp.supplier_id, sp.id, sp.amount_paid, 'BANK', sp.purchase_date, 'Demo payment', @uid, NOW(6), NOW(6)
FROM seed_purchases sp WHERE sp.shop_id = @shop AND sp.created_by = @uid AND sp.amount_paid > 0;

-- ---------------------------------------------------------------- PRODUCTION BATCHES + COSTING
INSERT INTO batches (shop_id, code, oil_type_id, seed_type_id, seed_kg, seed_cost, press_start, oil_litres, khali_kg, yield_pct,
                     settling_start, ready_date, mfg_date, best_before_date, status, notes, created_at, updated_at)
SELECT @shop, b.code, ot.id, st.id, b.kg, b.kg * b.rate, TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL b.dago DAY), '07:00:00'),
       b.litres, b.khali, ROUND(b.litres * ot.density / b.kg * 100, 2),
       IF(b.st = 'PRESSING', NULL, TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL b.dago DAY), '17:00:00')),
       IF(b.st IN ('READY', 'BOTTLED', 'SOLD_OUT'), DATE_SUB(CURDATE(), INTERVAL b.dago - 4 DAY), NULL),
       DATE_SUB(CURDATE(), INTERVAL b.dago DAY), DATE_ADD(DATE_SUB(CURDATE(), INTERVAL b.dago DAY), INTERVAL 365 DAY),
       b.st, b.note, NOW(6), NOW(6)
FROM (
    SELECT 'DEMO-B1' code, 'Mustard' oil, 'Mustard' seed, 200.000 kg, 62.00 rate, 70.000 litres, 128.000 khali, 40 dago, 'SOLD_OUT' st, 'Good clarity'        note UNION ALL
    SELECT 'DEMO-B2',      'Groundnut',   'Groundnut',     150.000,    95.00,     55.000,        90.000,        14,      'BOTTLED',     'Bottled 1L + 5L'          UNION ALL
    SELECT 'DEMO-B3',      'Sesame',      'Sesame',        80.000,     140.00,    30.000,        48.000,        6,       'READY',       'Settled, awaiting bottling' UNION ALL
    SELECT 'DEMO-B4',      'Mustard',     'Mustard',       250.000,    63.00,     88.000,        155.000,       2,       'SETTLING',    'Settling in tank 2'       UNION ALL
    SELECT 'DEMO-B5',      'Groundnut',   'Groundnut',     100.000,    95.00,     0.000,         0.000,         0,       'PRESSING',    'Pressing in progress'
) b
JOIN oil_types ot ON ot.name_en = b.oil
JOIN seed_types st ON st.name_en = b.seed;

INSERT INTO cost_defaults (shop_id, electricity_mode, electricity_value, labour_mode, labour_value, other_mode, other_value, khali_value_per_kg, target_margin_pct, created_at, updated_at)
SELECT @shop, 'PER_BATCH', 350.00, 'PER_BATCH', 600.00, 'PER_BATCH', 100.00, 28.00, 30.00, NOW(6), NOW(6)
WHERE NOT EXISTS (SELECT 1 FROM cost_defaults WHERE shop_id = @shop);

INSERT INTO batch_costs (batch_id, shop_id, seed_cost, electricity_cost, labour_cost, other_cost, khali_credit, total_cost, cost_per_litre, manual_override, computed_at, created_at, updated_at)
SELECT b.id, @shop, b.seed_cost, 350, 600, 100, b.khali_kg * 28,
       b.seed_cost + 1050 - b.khali_kg * 28,
       IF(b.oil_litres > 0, (b.seed_cost + 1050 - b.khali_kg * 28) / b.oil_litres, 0), 0, NOW(6), NOW(6), NOW(6)
FROM batches b WHERE b.shop_id = @shop AND b.code LIKE 'DEMO-B%' AND b.status <> 'PRESSING';

INSERT INTO batch_quality_notes (batch_id, note, checked_by, quality_level, created_at)
SELECT b.id, 'Colour and aroma checked, no sediment', 'Owner', 'GOOD', NOW(6)
FROM batches b WHERE b.shop_id = @shop AND b.code IN ('DEMO-B1', 'DEMO-B2');

-- ---------------------------------------------------------------- PACKAGING
INSERT IGNORE INTO packaging_items (shop_id, name, type, size, stock_qty, cost_per_unit, low_threshold, active, created_at, updated_at) VALUES
(@shop, 'PET Bottle 1L',      'bottle', '1L',    420, 9.50,  100, 1, NOW(6), NOW(6)),
(@shop, 'Glass Bottle 500ml', 'bottle', '500ml', 180, 18.00, 100, 1, NOW(6), NOW(6)),
(@shop, 'Tin Can 5L',         'tin',    '5L',     35, 85.00,  40, 1, NOW(6), NOW(6)),
(@shop, 'Bottle Cap 38mm',    'cap',    '38mm',  900, 1.20,  300, 1, NOW(6), NOW(6)),
(@shop, 'Front Label',        'label',  '10x6cm',  60, 0.80, 200, 1, NOW(6), NOW(6)),
(@shop, 'Carton 12 x 1L',     'carton', '12x1L',   25, 38.00,  20, 1, NOW(6), NOW(6));

-- ---------------------------------------------------------------- EXPIRY ALERTS (batch near/over expiry)
INSERT IGNORE INTO product_batch_stock (shop_id, product_id, batch_id, qty_available, qty_reserved, cost_price, received_date, expiry_date, best_before_date, active, created_at, updated_at)
SELECT @shop, p.id, v.batch, v.qty, 0, p.cost_price, DATE_SUB(CURDATE(), INTERVAL 340 DAY),
       DATE_ADD(CURDATE(), INTERVAL v.exp DAY), DATE_ADD(CURDATE(), INTERVAL v.exp DAY), 1, NOW(6), NOW(6)
FROM (
    SELECT 'MUS-1L' sku, 'DEMO-OLD-MUS' batch, 14.000 qty, 9  exp UNION ALL
    SELECT 'GNUT-1L',    'DEMO-OLD-GNUT',      8.000,      25     UNION ALL
    SELECT 'COC-500',    'DEMO-OLD-COC',       6.000,      -3
) v JOIN products p ON p.shop_id = @shop AND p.sku = v.sku;

-- ---------------------------------------------------------------- LOSSES
INSERT INTO loss_ledger (shop_id, loss_type, product_id, batch_id, qty, value, reference_no, description, loss_date, recorded_by, created_at)
SELECT @shop, l.t, p.id, l.batch, l.qty, l.qty * p.cost_price, l.ref, l.descr, DATE_SUB(CURDATE(), INTERVAL l.dago DAY), @uid, NOW(6)
FROM (
    SELECT 'LEAKAGE' t, 'MUS-1L' sku, 'DEMO-OLD-MUS' batch, 2.000 qty, 'LOSS-001' ref, 'Cap loose, oil leaked in storage' descr, 5 dago UNION ALL
    SELECT 'DAMAGE',    'SES-500',     NULL,                 1.000,     'LOSS-002',      'Glass bottle broke',                 3
) l JOIN products p ON p.shop_id = @shop AND p.sku = l.sku;

-- ---------------------------------------------------------------- RETURNS / COMPLAINTS
INSERT INTO returns (shop_id, return_no, source_type, source_id, customer_id, type, status, resolution, description, refund_amount,
                     reported_by, resolved_by, resolved_at, created_at, updated_at)
SELECT @shop, r.no, 'SALE', s.id, s.customer_id, r.t, r.st, r.res, r.descr, r.refund, @uid,
       IF(r.st = 'RESOLVED', @uid, NULL), IF(r.st = 'RESOLVED', NOW(6), NULL), NOW(6), NOW(6)
FROM (
    SELECT 'DEMO-R01' no, 'DEMO-S04' bill, 'LEAKAGE' t,    'RESOLVED' st, 'REFUND' res,  'One bottle leaked in transit' descr, 190.00 refund UNION ALL
    SELECT 'DEMO-R02',    'DEMO-S10',      'TASTE_SMELL',  'IN_REVIEW',   NULL,          'Customer says smell is strong',     NULL           UNION ALL
    SELECT 'DEMO-R03',    'DEMO-S08',      'QTY_SHORT',    'OPEN',        NULL,          'Delivered quantity looked less',    NULL
) r JOIN sales s ON s.shop_id = @shop AND s.bill_no = r.bill;

INSERT INTO return_items (return_id, shop_id, product_id, batch_id, qty, unit_price, action, created_at)
SELECT r.id, @shop, si.product_id, NULL, 1.000, si.rate, IF(r.status = 'RESOLVED', 'WRITE_OFF', NULL), NOW(6)
FROM returns r
JOIN sales s ON s.id = r.source_id AND r.source_type = 'SALE'
JOIN sale_items si ON si.sale_id = s.id
WHERE r.shop_id = @shop AND r.return_no LIKE 'DEMO-R%';

-- ---------------------------------------------------------------- LICENCES
INSERT INTO licences (shop_id, licence_number, type, issuer, issue_date, expiry_date, fssai_gstin, status, notes, created_by, created_at, updated_at)
SELECT @shop, l.num, l.t, l.issuer, DATE_SUB(CURDATE(), INTERVAL l.issued DAY), DATE_ADD(CURDATE(), INTERVAL l.left_days DAY), l.reg,
       CASE WHEN l.left_days < 0 THEN 'EXPIRED' WHEN l.left_days <= 30 THEN 'EXPIRING_SOON' ELSE 'ACTIVE' END,
       l.note, @uid, NOW(6), NOW(6)
FROM (
    SELECT 'DEMO-FSSAI-11522' num, 'FSSAI' t, 'FSSAI Maharashtra' issuer, 300 issued, 420 left_days, '11522033000123' reg, 'Main food licence'  note UNION ALL
    SELECT 'DEMO-GST-27AAAPL', 'GST',        'GST Dept',            800,        2000,  '27AAAPL1234C1Z5',        'GST registration'               UNION ALL
    SELECT 'DEMO-SHOP-EST-77', 'SHOP_ESTABLISHMENT', 'PMC Pune',    330,        22,    NULL,                      'Renew soon'                     UNION ALL
    SELECT 'DEMO-WS-STAMP-09', 'WEIGHING_SCALE_STAMPING', 'Legal Metrology', 400, -12, NULL,                      'Scale stamping overdue'
) l;

-- ---------------------------------------------------------------- CASH BOOK
INSERT IGNORE INTO cash_book_days (shop_id, day_date, status, opening_balance, total_in, total_out, closing_balance, closed_by, closed_at, notes, created_at, updated_at)
SELECT @shop, DATE_SUB(CURDATE(), INTERVAL 1 DAY), 'CLOSED', 5000.00, 2600.00, 800.00, 6800.00, @uid, NOW(6), 'Demo closed day', NOW(6), NOW(6);
INSERT IGNORE INTO cash_book_days (shop_id, day_date, status, opening_balance, total_in, total_out, created_at, updated_at)
SELECT @shop, CURDATE(), 'OPEN', 6800.00, 0.00, 0.00, NOW(6), NOW(6);
