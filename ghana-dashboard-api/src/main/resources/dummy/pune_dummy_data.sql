-- Dummy data: Lakadi Ghana (wood cold-pressed oil) shop, Pune
-- NOT a Flyway migration. Run manually against a dev DB after V1..V17 are applied:
--   mysql -u <user> -p <db> < src/main/resources/dummy/pune_dummy_data.sql
-- Target shop: pass --init-command="SET @shop=<id>" to mysql; defaults to shop 1. Safe to re-run (INSERT IGNORE on unique keys).

SET @shop := IFNULL(@shop, 1);


-- Products (price list for a wood-pressed oil shop)
INSERT IGNORE INTO products
    (shop_id, oil_type_id, name, sku, pack_size, unit, container, selling_price, mrp, cost_price, low_stock_threshold, active, is_khali, created_at, updated_at)
SELECT @shop, ot.id, p.name, p.sku, p.pack_size, p.unit, p.container, p.sp, p.mrp, p.cp, p.low, 1, 0, NOW(6), NOW(6)
FROM (
    SELECT 'Mustard'   en, 'Lakadi Ghana Mustard Oil 1L'   name, 'MUS-1L'   sku, 1.000 pack_size, 'L' unit, 'plastic' container, 210.00 sp, 230.00 mrp, 165.00 cp, 20.000 low UNION ALL
    SELECT 'Mustard',      'Lakadi Ghana Mustard Oil 5L',        'MUS-5L',   5.000, 'L', 'tin',     1020.00, 1100.00, 810.00, 8.000 UNION ALL
    SELECT 'Groundnut',    'Lakadi Ghana Groundnut Oil 1L',      'GNUT-1L',  1.000, 'L', 'plastic',  240.00,  260.00, 190.00, 20.000 UNION ALL
    SELECT 'Groundnut',    'Lakadi Ghana Groundnut Oil 5L',      'GNUT-5L',  5.000, 'L', 'tin',     1180.00, 1260.00, 940.00, 8.000 UNION ALL
    SELECT 'Sesame',       'Lakadi Ghana Sesame (Til) Oil 500ml','SES-500',  0.500, 'L', 'glass',    230.00,  250.00, 180.00, 15.000 UNION ALL
    SELECT 'Sesame',       'Lakadi Ghana Sesame (Til) Oil 1L',   'SES-1L',   1.000, 'L', 'glass',    440.00,  480.00, 350.00, 15.000 UNION ALL
    SELECT 'Coconut',      'Lakadi Ghana Coconut Oil 500ml',     'COC-500',  0.500, 'L', 'glass',    220.00,  240.00, 170.00, 10.000 UNION ALL
    SELECT 'Flaxseed',     'Lakadi Ghana Flaxseed (Alsi) Oil 250ml','FLX-250',0.250, 'L', 'glass',   180.00,  200.00, 135.00, 10.000 UNION ALL
    SELECT 'Sunflower',    'Lakadi Ghana Sunflower Oil 1L',      'SUN-1L',   1.000, 'L', 'plastic',  190.00,  210.00, 150.00, 20.000
) p
JOIN oil_types ot ON ot.name_en = p.en;

-- Khali (oil cake) by-products
INSERT IGNORE INTO products
    (shop_id, oil_type_id, name, sku, pack_size, unit, container, selling_price, mrp, cost_price, low_stock_threshold, active, is_khali, created_at, updated_at)
SELECT @shop, ot.id, 'Mustard Khali (Cattle Feed) 50kg', 'KHALI-MUS-50', 50.000, 'KG', 'bag', 1900.00, 2100.00, 1500.00, 5.000, 1, 1, NOW(6), NOW(6)
FROM oil_types ot WHERE ot.name_en = 'Mustard';

-- Opening batch stock for every non-khali product
INSERT IGNORE INTO product_batch_stock
    (shop_id, product_id, batch_id, qty_available, qty_reserved, cost_price, received_date, expiry_date, best_before_date, active, created_at, updated_at)
SELECT @shop, pr.id, CONCAT('PUN-', DATE_FORMAT(CURDATE(), '%y%m'), '-', pr.sku),
       CASE WHEN pr.sku IN ('SES-1L','FLX-250') THEN 12 ELSE 60 END, 0, pr.cost_price,
       DATE_SUB(CURDATE(), INTERVAL 10 DAY), DATE_ADD(CURDATE(), INTERVAL 355 DAY), DATE_ADD(CURDATE(), INTERVAL 340 DAY),
       1, NOW(6), NOW(6)
FROM products pr
WHERE pr.shop_id = @shop AND pr.is_khali = 0 AND pr.sku IN
    ('MUS-1L','MUS-5L','GNUT-1L','GNUT-5L','SES-500','SES-1L','COC-500','FLX-250','SUN-1L');

-- Customers around Pune (Gate 2 / Gate 3 landmarks)
INSERT IGNORE INTO customers (shop_id, name, phone, email, preferred_language, active, created_at, updated_at) VALUES
(@shop, 'Rohit Deshmukh',     '9890100001', 'rohit.deshmukh@example.com', 'mr', 1, NOW(6), NOW(6)),
(@shop, 'Sunita Kulkarni',    '9890100002', NULL,                         'mr', 1, NOW(6), NOW(6)),
(@shop, 'Amit Joshi',         '9890100003', 'amit.joshi@example.com',     'en', 1, NOW(6), NOW(6)),
(@shop, 'Priya Patil',        '9890100004', NULL,                         'mr', 1, NOW(6), NOW(6)),
(@shop, 'Mahesh Gaikwad',     '9890100005', NULL,                         'hi', 1, NOW(6), NOW(6)),
(@shop, 'Shree Annapurna Mess','9890100006','annapurna.mess@example.com', 'mr', 1, NOW(6), NOW(6)),
(@shop, 'Kavita Bhosale',     '9890100007', NULL,                         'mr', 1, NOW(6), NOW(6)),
(@shop, 'Vikram Shinde',      '9890100008', NULL,                         'en', 1, NOW(6), NOW(6));

INSERT INTO customer_addresses
    (customer_id, shop_id, address_type, address_line1, address_line2, city, state, pincode, landmark, is_default, active, created_at, updated_at)
SELECT c.id, @shop, 'HOME', a.line1, a.line2, 'Pune', 'Maharashtra', a.pin, a.landmark, 1, 1, NOW(6), NOW(6)
FROM (
    SELECT '9890100001' phone, 'Flat 12, Sai Residency'      line1, 'Market Yard Road'   line2, '411037' pin, 'Near Market Yard Gate 2'  landmark UNION ALL
    SELECT '9890100002', 'Wing B-304, Gangadham Society',          'Bibwewadi',               '411037',      'Near Gangadham Chowk Gate 3'  UNION ALL
    SELECT '9890100003', '5, Shivtirth Nagar',                     'Kothrud',                 '411038',      'Opp. Gate 3, Karve Road'      UNION ALL
    SELECT '9890100004', 'Row House 7, Lokmanya Colony',           'Kothrud',                 '411038',      'Near Gate 2 Garden'           UNION ALL
    SELECT '9890100005', 'Chawl 14, Gultekdi',                     'Mukund Nagar',            '411037',      'Behind Gate 2 Bus Stop'       UNION ALL
    SELECT '9890100006', 'Shop 3, Swargate Food Court',            'Swargate',                '411042',      'Near Gate 3 Swargate ST Stand' UNION ALL
    SELECT '9890100007', 'Flat 21, Dhanlaxmi Apts',                'Sahakar Nagar',           '411009',      'Near Gate 2 Parvati Paytha'   UNION ALL
    SELECT '9890100008', 'A-101, Sunshine Heights',                'Katraj',                  '411046',      'Near Gate 3 Katraj Dairy'
) a
JOIN customers c ON c.shop_id = @shop AND c.phone = a.phone
WHERE NOT EXISTS (SELECT 1 FROM customer_addresses ca WHERE ca.customer_id = c.id);
