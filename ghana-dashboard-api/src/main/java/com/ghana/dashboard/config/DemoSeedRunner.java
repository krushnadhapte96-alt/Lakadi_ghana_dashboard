package com.ghana.dashboard.config;

import com.ghana.dashboard.domain.Batch;
import com.ghana.dashboard.domain.BatchCost;
import com.ghana.dashboard.domain.BatchQualityNote;
import com.ghana.dashboard.domain.Customer;
import com.ghana.dashboard.domain.CustomerAddress;
import com.ghana.dashboard.domain.Expense;
import com.ghana.dashboard.domain.OilType;
import com.ghana.dashboard.domain.Order;
import com.ghana.dashboard.domain.OrderItem;
import com.ghana.dashboard.domain.OrderStatusHistory;
import com.ghana.dashboard.domain.PackagingItem;
import com.ghana.dashboard.domain.Product;
import com.ghana.dashboard.domain.ProductBatchStock;
import com.ghana.dashboard.domain.ProductPackaging;
import com.ghana.dashboard.domain.Return;
import com.ghana.dashboard.domain.ReturnItem;
import com.ghana.dashboard.domain.Role;
import com.ghana.dashboard.domain.Sale;
import com.ghana.dashboard.domain.SaleItem;
import com.ghana.dashboard.domain.SeedPurchase;
import com.ghana.dashboard.domain.SeedStock;
import com.ghana.dashboard.domain.SeedType;
import com.ghana.dashboard.domain.Shop;
import com.ghana.dashboard.domain.Supplier;
import com.ghana.dashboard.domain.SupplierPayment;
import com.ghana.dashboard.domain.User;
import com.ghana.dashboard.domain.Payment;
import com.ghana.dashboard.common.JdbcSupport;
import com.ghana.dashboard.repository.BatchCostRepository;
import com.ghana.dashboard.repository.BatchQualityNoteRepository;
import com.ghana.dashboard.repository.BatchRepository;
import com.ghana.dashboard.repository.CustomerAddressRepository;
import com.ghana.dashboard.repository.CustomerRepository;
import com.ghana.dashboard.repository.ExpenseRepository;
import com.ghana.dashboard.repository.OilTypeRepository;
import com.ghana.dashboard.repository.OrderItemRepository;
import com.ghana.dashboard.repository.OrderRepository;
import com.ghana.dashboard.repository.OrderStatusHistoryRepository;
import com.ghana.dashboard.repository.PackagingItemRepository;
import com.ghana.dashboard.repository.ProductBatchStockRepository;
import com.ghana.dashboard.repository.ProductPackagingRepository;
import com.ghana.dashboard.repository.ProductRepository;
import com.ghana.dashboard.repository.ReturnItemRepository;
import com.ghana.dashboard.repository.ReturnRepository;
import com.ghana.dashboard.repository.SaleItemRepository;
import com.ghana.dashboard.repository.SaleRepository;
import com.ghana.dashboard.repository.SeedPurchaseRepository;
import com.ghana.dashboard.repository.SeedStockRepository;
import com.ghana.dashboard.repository.SeedTypeRepository;
import com.ghana.dashboard.repository.ShopRepository;
import com.ghana.dashboard.repository.SupplierPaymentRepository;
import com.ghana.dashboard.repository.SupplierRepository;
import com.ghana.dashboard.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Demo dataset for local development.
 *
 * <p>Idempotent and development-only: the runner never touches production, and it writes nothing
 * when a shop named "Shri Krishna Lakdi Ghana" already exists, so restarting the app is safe.
 * Everything is written through the same JPA entities / SQL conventions the real services use, so
 * every screen reads the same numbers the services would produce.
 */
@Component
@Profile("dev")
public class DemoSeedRunner implements CommandLineRunner {

    public static final String DEMO_SHOP_NAME = "Shri Krishna Lakdi Ghana";
    private static final Logger log = LoggerFactory.getLogger(DemoSeedRunner.class);

    private final ShopRepository shops;
    private final UserRepository users;
    private final OilTypeRepository oilTypes;
    private final SeedTypeRepository seedTypes;
    private final SupplierRepository suppliers;
    private final PackagingItemRepository packagingItems;
    private final ProductRepository products;
    private final ProductPackagingRepository productPackaging;
    private final ProductBatchStockRepository batchStocks;
    private final SeedStockRepository seedStock;
    private final SeedPurchaseRepository purchases;
    private final SupplierPaymentRepository supplierPayments;
    private final CustomerRepository customers;
    private final CustomerAddressRepository addresses;
    private final OrderRepository orders;
    private final OrderItemRepository orderItems;
    private final OrderStatusHistoryRepository orderHistory;
    private final SaleRepository sales;
    private final SaleItemRepository saleItems;
    private final BatchRepository batches;
    private final BatchCostRepository batchCosts;
    private final BatchQualityNoteRepository qualityNotes;
    private final ExpenseRepository expenses;
    private final ReturnRepository returns;
    private final ReturnItemRepository returnItems;
    private final PasswordEncoder passwordEncoder;
    private final JdbcSupport db;

    public DemoSeedRunner(ShopRepository shops, UserRepository users, OilTypeRepository oilTypes,
                          SeedTypeRepository seedTypes, SupplierRepository suppliers,
                          PackagingItemRepository packagingItems, ProductRepository products,
                          ProductPackagingRepository productPackaging,
                          ProductBatchStockRepository batchStocks, SeedStockRepository seedStock,
                          SeedPurchaseRepository purchases, SupplierPaymentRepository supplierPayments,
                          CustomerRepository customers, CustomerAddressRepository addresses,
                          OrderRepository orders, OrderItemRepository orderItems,
                          OrderStatusHistoryRepository orderHistory, SaleRepository sales,
                          SaleItemRepository saleItems, BatchRepository batches,
                          BatchCostRepository batchCosts, BatchQualityNoteRepository qualityNotes,
                          ExpenseRepository expenses, ReturnRepository returns,
                          ReturnItemRepository returnItems, PasswordEncoder passwordEncoder, JdbcSupport db) {
        this.shops = shops;
        this.users = users;
        this.oilTypes = oilTypes;
        this.seedTypes = seedTypes;
        this.suppliers = suppliers;
        this.packagingItems = packagingItems;
        this.products = products;
        this.productPackaging = productPackaging;
        this.batchStocks = batchStocks;
        this.seedStock = seedStock;
        this.purchases = purchases;
        this.supplierPayments = supplierPayments;
        this.customers = customers;
        this.addresses = addresses;
        this.orders = orders;
        this.orderItems = orderItems;
        this.orderHistory = orderHistory;
        this.sales = sales;
        this.saleItems = saleItems;
        this.batches = batches;
        this.batchCosts = batchCosts;
        this.qualityNotes = qualityNotes;
        this.expenses = expenses;
        this.returns = returns;
        this.returnItems = returnItems;
        this.passwordEncoder = passwordEncoder;
        this.db = db;
    }

    @Override
    @Transactional
    public void run(String... args) {
        boolean exists = shops.findAll().stream()
                .anyMatch(s -> DEMO_SHOP_NAME.equalsIgnoreCase(s.getName()));
        if (exists) {
            log.info("Demo shop already present - skipping demo seed.");
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        Random rnd = new Random(20261003L);
        LocalDate today = LocalDate.now();

        // ── Shop ─────────────────────────────────────────────────────────────
        Shop shop = new Shop();
        shop.setName(DEMO_SHOP_NAME);
        shop.setMobile("9876501234");
        shop.setAddress("Station Road, Ward 4, Harda, MP");
        shop.setGstin("23ABCDE1234F1Z5");
        shop.setCurrencyCode("INR");
        shop.setStatus("ACTIVE");
        shop.setCreatedBy("demo-seed");
        shop.setCreatedAt(now.minusDays(120));
        shop.setUpdatedAt(now);
        shop = shops.save(shop);
        Long shopId = shop.getId();

        User ownerE = user(shopId, "Ramesh Gupta", "9876501234", "owner", Role.OWNER, "Owner@2026");
        User staffE = user(shopId, "Suresh Yadav", "9812345670", "staff", Role.STAFF, "Staff@2026");
        User owner = users.save(ownerE);
        User staff = users.save(staffE);
        Long ownerId = owner.getId();
        Long staffId = staff.getId();

        List<OilType> oils = oilTypes.findAll();
        List<SeedType> seeds = seedTypes.findAll();

        // ── Suppliers ─────────────────────────────────────────────────────────
        Supplier s1 = supplier(shopId, "Kisan Traders", "9000011111", "Harda Mandi");
        Supplier s2 = supplier(shopId, "MP Oilseeds Co-op", "9000022222", "Indore");
        Supplier s3 = supplier(shopId, "Green Valley Farms", "9000033333", "Betul");
        List<Supplier> sup = List.of(
                suppliers.save(s1), suppliers.save(s2), suppliers.save(s3));

        // ── Packaging items ───────────────────────────────────────────────────
        PackagingItem tin15 = packagingItem(shopId, "15 L Tin", PackagingItem.PackagingType.tin, "15L");
        PackagingItem bottle1 = packagingItem(shopId, "1 L Bottle", PackagingItem.PackagingType.bottle, "1L");
        PackagingItem pouch500 = packagingItem(shopId, "500 ml Pouch", PackagingItem.PackagingType.bag, "500ml");
        List<PackagingItem> packs = List.of(
                packagingItems.save(tin15), packagingItems.save(bottle1), packagingItems.save(pouch500));

        // ── Products (incl. khali + packaging mapping) ────────────────────────
        List<Product> prods = new ArrayList<>();
        String[][] oilsSpec = {
                {"Mustard Oil 15L Tin", "MOIL15", "15", "2100", "1800"},
                {"Mustard Oil 1L Bottle", "MOIL1", "1", "165", "132"},
                {"Groundnut Oil 1L Bottle", "GNOIL1", "1", "170", "138"},
                {"Sesame Oil 500ml", "SEOIL500", "0.5", "320", "270"},
                {"Coconut Oil 1L Bottle", "CCOIL1", "1", "190", "155"},
                {"Flaxseed Oil 500ml", "FLOIL500", "0.5", "300", "255"},
                {"Sunflower Oil 15L Tin", "SOIL15", "15", "1900", "1620"},
                {"Palm Oil 1L Bottle", "POIL1", "1", "150", "120"},
                {"Khali (Mustard press cake)", "KHALI1", "1", "80", "62"},
                {"Mustard Oil 500ml Pouch", "MOIL500P", "0.5", "85", "70"},
                {"Groundnut Oil 500ml Pouch", "GNOIL500P", "0.5", "88", "72"},
                {"Sesame Oil 15L Tin", "SEOIL15", "15", "8900", "7600"},
        };
        for (int i = 0; i < oilsSpec.length; i++) {
            String[] s = oilsSpec[i];
            Product p = new Product();
            p.setShopId(shopId);
            p.setOilType(oils.get(i % oils.size()));
            p.setName(s[0]);
            p.setSku(s[1]);
            p.setPackSize(new BigDecimal(s[2]));
            p.setUnit(Product.Unit.L);
            p.setContainer((i % 2 == 0) ? Product.Container.tin : Product.Container.plastic);
            p.setSellingPrice(new BigDecimal(s[3]));
            p.setMrp(new BigDecimal(s[4]));
            p.setCostPrice(new BigDecimal(s[4]).multiply(new BigDecimal("0.85")).setScale(2, RoundingMode.HALF_UP));
            p.setLowStockThreshold(new BigDecimal("5"));
            p.setActive(true);
            p.setKhali(i == 8);
            p.setCreatedAt(now.minusDays(115));
            p.setUpdatedAt(now);
            prods.add(products.save(p));

            ProductPackaging pp = new ProductPackaging();
            pp.setShopId(shopId);
            pp.setProduct(prods.get(i));
            pp.setPackagingItem(packs.get(i % packs.size()));
            pp.setUnitsPerPack(BigDecimal.ONE);
            pp.setActive(true);
            pp.setCreatedAt(now.minusDays(115));
            pp.setUpdatedAt(now);
            productPackaging.save(pp);
        }

        // ── Stock per product; two products drop to ~expiry soon ──────────────
        for (int i = 0; i < prods.size(); i++) {
            Product p = prods.get(i);
            ProductBatchStock bs = new ProductBatchStock();
            bs.setShopId(shopId);
            bs.setProduct(p);
            bs.setBatchId("BATCH-" + (1000 + i));
            bs.setQtyAvailable(new BigDecimal(String.valueOf(5 + rnd.nextInt(40))));
            bs.setQtyReserved(BigDecimal.ZERO);
            bs.setCostPrice(p.getCostPrice());
            bs.setReceivedDate(today.minusDays(30 + rnd.nextInt(30)));
            bs.setExpiryDate(i < 2 ? today.plusDays(9 + i * 4) : today.plusMonths(6));
            bs.setBestBeforeDate(bs.getExpiryDate());
            bs.setActive(true);
            bs.setCreatedAt(now.minusDays(30));
            bs.setUpdatedAt(now);
            batchStocks.save(bs);
        }

        // ── Seed stock per seed type ──────────────────────────────────────────
        for (SeedType st : seeds) {
            SeedStock ss = new SeedStock();
            ss.setShopId(shopId);
            ss.setSeedType(st);
            ss.setAvailableKg(new BigDecimal(200 + rnd.nextInt(1800)).setScale(3, RoundingMode.HALF_UP));
            ss.setAvgRate(new BigDecimal("95"));
            ss.setLowStockThreshold(new BigDecimal("100.000"));
            ss.setUpdatedAt(now);
            seedStock.save(ss);
        }

        // ── Seed purchases over ~90 days ─────────────────────────────────────
        for (int i = 0; i < 12; i++) {
            Supplier sp = sup.get(i % 3);
            SeedType st = seeds.get(i % seeds.size());
            BigDecimal qty = new BigDecimal(50 + rnd.nextInt(450)).setScale(3, RoundingMode.HALF_UP);
            BigDecimal rate = new BigDecimal(80 + rnd.nextInt(60)).setScale(2, RoundingMode.HALF_UP);
            BigDecimal total = qty.multiply(rate).setScale(2, RoundingMode.HALF_UP);
            SeedPurchase.PaymentStatus status = switch (i % 3) {
                case 0 -> SeedPurchase.PaymentStatus.PAID;
                case 1 -> SeedPurchase.PaymentStatus.PARTIAL;
                default -> SeedPurchase.PaymentStatus.UNPAID;
            };
            SeedPurchase spch = new SeedPurchase();
            spch.setShopId(shopId);
            spch.setSeedType(st);
            spch.setSupplier(sp);
            spch.setQtyKg(qty);
            spch.setRatePerKg(rate);
            spch.setTotalAmount(total);
            spch.setMoisturePercent(new BigDecimal(String.valueOf(8 + rnd.nextInt(5))));
            spch.setGrade(i % 3 == 0 ? SeedPurchase.Grade.A : i % 3 == 1 ? SeedPurchase.Grade.B : SeedPurchase.Grade.C);
            spch.setPurchaseDate(today.minusDays(rnd.nextInt(90)));
            spch.setPaymentStatus(status);
            spch.setAmountPaid(status == SeedPurchase.PaymentStatus.PAID ? total
                    : status == SeedPurchase.PaymentStatus.PARTIAL ? total.multiply(new BigDecimal("0.5")).setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO);
            spch.setCreatedBy(ownerId);
            spch.setCreatedAt(now.minusDays(rnd.nextInt(60)));
            spch.setUpdatedAt(now);
            purchases.save(spch);
        }

        // ── A few supplier payments ───────────────────────────────────────────
        SupplierPayment pay1 = supplierPayment(shopId, s1.getId(), "25000", ownerId);
        supplierPayments.save(pay1);
        SupplierPayment pay2 = supplierPayment(shopId, s2.getId(), "18000", ownerId);
        supplierPayments.save(pay2);

        // ── Customers + addresses ─────────────────────────────────────────────
        List<Customer> custs = new ArrayList<>();
        List<CustomerAddress> addrList = new ArrayList<>();
        String[] names = {"Anwar Store", "Laxmi General", "Om Sai Mart", "Priya Kirana", "Gupta Hotel",
                "Shree Om Restaurant", "Balaji Bazar", "Shiv Dairy", "Tulsi Stores", "Krishna Agency",
                "Neha Provision", "Deepak Mart", "Raj Caterers", "Asha Bhandar", "New Star Store",
                "Gokul Kendra", "Himalaya Store", "Om Namah Mart", "Sri Balaji", "Anand Pita"};
        for (int i = 0; i < names.length; i++) {
            Customer c = new Customer();
            c.setShopId(shopId);
            c.setName(names[i]);
            c.setPhone("900000" + String.format("%04d", 1000 + i));
            c.setActive(true);
            c.setCreatedAt(now.minusDays(90));
            c.setUpdatedAt(now);
            custs.add(customers.save(c));
            if (i < custs.size()) {
                CustomerAddress a = new CustomerAddress();
                a.setCustomer(custs.get(i));
                a.setShopId(shopId);
                a.setAddressType(CustomerAddress.AddressType.HOME);
                a.setAddressLine1((i + 5) + " MG Road");
                a.setCity("Harda");
                a.setState("MP");
                a.setPincode("461331");
                a.setActive(true);
                a.setCreatedAt(now.minusDays(80));
                a.setUpdatedAt(now);
                addrList.add(addresses.save(a));
            }
        }

        // ── Khata parties (JDBC, same pattern as the tests) ─────────────────
        String[][] parties = {
                {"Anwar Store", "RETAILER", "9000001001", "15000"},
                {"Laxmi General", "RETAILER", "9000001002", "8200"},
                {"Gupta Hotel", "HOTEL", "9000001003", "23500"},
                {"Raj Caterers", "RESTAURANT", "9000001004", "4100"},
                {"Walk-in credit", "INDIVIDUAL", "9000001005", "950"},
        };
        for (int i = 0; i < parties.length; i++) {
            String[] p = parties[i];
            db.jdbc().update("""
                    INSERT INTO khata_parties (shop_id, name, type, phone, address, credit_limit,
                                               payment_terms_days, opening_balance, active, created_at, updated_at, version)
                    VALUES (:shop, :name, :type, :phone, NULL, 30000, 30, 0, 1, :now, :now, 0)""",
                    new MapSqlParameterSource()
                            .addValue("shop", shopId)
                            .addValue("name", p[0])
                            .addValue("type", p[1])
                            .addValue("phone", p[2])
                            .addValue("now", now));
            long partyId = db.jdbc().queryForObject(
                    "SELECT id FROM khata_parties WHERE shop_id=:shop AND phone=:phone ORDER BY id DESC LIMIT 1",
                    new MapSqlParameterSource().addValue("shop", shopId).addValue("phone", p[2]), Long.class);
            db.jdbc().update("""
                    INSERT INTO khata_entries (shop_id, party_id, entry_type, amount, delta, entry_date,
                                               due_date, source_type, source_id, bill_no, created_at)
                    VALUES (:shop, :party, 'CREDIT_SALE', :amount, :amount, :date, :due, NULL, NULL, NULL, :now)""",
                    new MapSqlParameterSource()
                            .addValue("shop", shopId)
                            .addValue("party", partyId)
                            .addValue("amount", new BigDecimal(p[3]))
                            .addValue("date", today.minusDays(10 + i * 3))
                            .addValue("due", today.minusDays(i % 2 == 0 ? 2 : -20))
                            .addValue("now", now));
            if (i % 2 == 0) {
                db.jdbc().update("""
                        INSERT INTO khata_entries (shop_id, party_id, entry_type, amount, delta, entry_date,
                                                   source_type, source_id, payment_mode, note, created_at)
                        VALUES (:shop, :party, 'PAYMENT_RECEIVED', :amount, :delta, :date, NULL, NULL, 'CASH', 'part payment', :now)""",
                        new MapSqlParameterSource()
                                .addValue("shop", shopId)
                                .addValue("party", partyId)
                                .addValue("amount", new BigDecimal(p[3]).multiply(new BigDecimal("0.4")).setScale(2, RoundingMode.HALF_UP))
                                .addValue("delta", new BigDecimal(p[3]).multiply(new BigDecimal("-0.4")).setScale(2, RoundingMode.HALF_UP))
                                .addValue("date", today.minusDays(3))
                                .addValue("now", now));
            }
        }

        // ── Sales & items over ~90 days ───────────────────────────────────────
        for (int i = 0; i < 60; i++) {
            LocalDate day = today.minusDays(rnd.nextInt(90));
            LocalDateTime at = day.atTime(10 + rnd.nextInt(9), rnd.nextInt(60));
            boolean udhaar = i % 6 == 0;
            Sale sale = new Sale();
            sale.setShopId(shopId);
            sale.setBillNo("INV-" + String.format("%04d", 1000 + i));
            sale.setUser(udhaar ? staff : (i % 2 == 0 ? owner : staff));
            sale.setCreatedAt(at);
            sale.setUpdatedAt(at);
            sale.setActive(true);
            sale.setVoid(false);
            BigDecimal subtotal = BigDecimal.ZERO;
            List<SaleItem> items = new ArrayList<>();
            int lineCount = 1 + rnd.nextInt(3);
            for (int l = 0; l < lineCount; l++) {
                Product p = prods.get(rnd.nextInt(prods.size()));
                BigDecimal qty = new BigDecimal(1 + rnd.nextInt(10));
                BigDecimal rate = p.getSellingPrice();
                BigDecimal amount = qty.multiply(rate);
                SaleItem it = new SaleItem();
                it.setSale(sale);
                it.setShopId(shopId);
                it.setProduct(p);
                it.setSku(p.getSku());
                it.setProductName(p.getName());
                it.setUnit(p.getUnit().name());
                it.setQty(qty);
                it.setRate(rate);
                it.setAmount(amount);
                it.setCostPrice(p.getCostPrice());
                it.setProfit(amount.subtract(qty.multiply(p.getCostPrice())));
                it.setCreatedAt(at);
                items.add(it);
                subtotal = subtotal.add(amount);
            }
            sale.setSubtotal(subtotal);
            sale.setDiscount(BigDecimal.ZERO);
            sale.setDiscountType(Sale.DiscountType.AMOUNT);
            sale.setTotalAmount(subtotal);
            sale.setPaidAmount(udhaar ? BigDecimal.ZERO : subtotal);
            sale.setPaymentMode(udhaar ? Sale.PaymentMode.UDHAAR :
                    (i % 3 == 0 ? Sale.PaymentMode.UPI : i % 3 == 1 ? Sale.PaymentMode.CASH : Sale.PaymentMode.CARD));
            sale.setPaymentStatus(udhaar ? Sale.PaymentStatus.UNPAID : Sale.PaymentStatus.PAID);
            Customer sc = udhaar ? custs.get(i % 5) : custs.get(5 + (i % 10));
            sale.setCustomer(sc);
            sales.save(sale);
            for (SaleItem it : items) {
                saleItems.save(it);
            }
            if (!udhaar) {
                db.jdbc().update("""
                        INSERT INTO payments (shop_id, direction, mode, amount, source_type, source_id,
                                              reference_no, payment_date, note, created_by, created_at, updated_at)
                        VALUES (:shop, 'IN', :mode, :amount, 'SALE', :sourceId, :ref, :date, :note, :user, :now, :now)""",
                        new MapSqlParameterSource()
                                .addValue("shop", shopId)
                                .addValue("mode", sale.getPaymentMode().name())
                                .addValue("amount", subtotal)
                                .addValue("sourceId", sale.getId())
                                .addValue("ref", sale.getBillNo())
                                .addValue("date", java.sql.Date.valueOf(day))
                                .addValue("note", "Counter sale " + sale.getBillNo())
                                .addValue("user", sale.getUser().getId())
                                .addValue("now", at));
            }
        }

        // ── Orders over ~90 days ──────────────────────────────────────────────
        for (int i = 0; i < 20; i++) {
            LocalDate day = today.minusDays(rnd.nextInt(90));
            Order order = new Order();
            order.setShopId(shopId);
            order.setBillNo("ORD-" + String.format("%04d", 2000 + i));
            Customer c = custs.get(i % custs.size());
            order.setCustomer(c);
            order.setAddress(addrList.stream()
                    .filter(a -> a.getCustomer().getId().equals(c.getId())).findFirst()
                    .orElse(addrList.get(0)));
            order.setUser(i % 2 == 0 ? owner : staff);
            BigDecimal subtotal = BigDecimal.ZERO;
            List<OrderItem> oItems = new ArrayList<>();
            int lines = 1 + rnd.nextInt(2);
            for (int l = 0; l < lines; l++) {
                Product p = prods.get(rnd.nextInt(prods.size()));
                BigDecimal qty = new BigDecimal(1 + rnd.nextInt(5));
                BigDecimal amount = qty.multiply(p.getSellingPrice());
                OrderItem oi = new OrderItem();
                oi.setOrder(order);
                oi.setShopId(shopId);
                oi.setProduct(p);
                oi.setSku(p.getSku());
                oi.setProductName(p.getName());
                oi.setUnit(p.getUnit().name());
                oi.setQty(qty);
                oi.setRate(p.getSellingPrice());
                oi.setAmount(amount);
                oi.setCostPrice(p.getCostPrice());
                oi.setProfit(amount.subtract(qty.multiply(p.getCostPrice())));
                oi.setCreatedAt(day.atTime(11, 0));
                oItems.add(oi);
                subtotal = subtotal.add(amount);
            }
            order.setSubtotal(subtotal);
            order.setDeliveryCharge(new BigDecimal("20"));
            order.setDiscount(BigDecimal.ZERO);
            order.setDiscountType(Order.DiscountType.AMOUNT);
            order.setTotalAmount(subtotal.add(new BigDecimal("20")));
            order.setPaidAmount(BigDecimal.ZERO);
            order.setPaymentMode(Order.PaymentMode.COD);
            order.setPaymentStatus(Order.PaymentStatus.UNPAID);
            Order.OrderStatus st = switch (i % 5) {
                case 0 -> Order.OrderStatus.NEW;
                case 1 -> Order.OrderStatus.CONFIRMED;
                case 2 -> Order.OrderStatus.OUT_FOR_DELIVERY;
                case 3 -> Order.OrderStatus.DELIVERED;
                default -> Order.OrderStatus.CANCELLED;
            };
            order.setStatus(st);
            order.setExpectedDeliveryDate((i % 4 == 0) ? today.minusDays(2) : day.plusDays(3));
            order.setActive(true);
            order.setCreatedAt(day.atTime(9, 30));
            order.setUpdatedAt(day.atTime(9, 30));
            orders.save(order);
            for (OrderItem oi : oItems) {
                orderItems.save(oi);
            }
            OrderStatusHistory h = new OrderStatusHistory();
            h.setOrder(order);
            h.setShopId(shopId);
            h.setNewStatus(st);
            h.setChangedBy(users.findById(ownerId).orElseThrow());
            h.setCreatedAt(day.atTime(9, 30));
            orderHistory.save(h);
        }

        // ── Batches, costs, quality notes ────────────────────────────────────
        String[][] batchSpec = {
                {"B-101", "500", "PRESSING"},
                {"B-102", "700", "SETTLING"},
                {"B-103", "620", "READY"},
                {"B-104", "800", "BOTTLED"},
                {"B-105", "300", "SOLD_OUT"},
                {"B-106", "450", "READY"},
                {"B-107", "550", "SETTLING"},
                {"B-108", "650", "PRESSING"},
        };
        for (int i = 0; i < batchSpec.length; i++) {
            String[] bs = batchSpec[i];
            OilType ot = oils.get(i % oils.size());
            SeedType st = seeds.stream().filter(s -> s.getOilType().getId().equals(ot.getId())).findFirst().orElse(seeds.get(i % seeds.size()));
            Batch b = new Batch();
            b.setShopId(shopId);
            b.setCode(bs[0]);
            b.setOilType(ot);
            b.setSeedType(st);
            b.setSeedKg(new BigDecimal(bs[1]));
            b.setSeedCost(new BigDecimal(bs[1]).multiply(new BigDecimal("95")));
            b.setPressStart(now.minusDays(20 - i));
            b.setOilLitres(new BigDecimal(bs[1]).multiply(new BigDecimal("0.38")).setScale(2, RoundingMode.HALF_UP));
            b.setKhaliKg(new BigDecimal(bs[1]).multiply(new BigDecimal("0.55")).setScale(2, RoundingMode.HALF_UP));
            b.setYieldPct(new BigDecimal("38"));
            b.setSettlingStart(now.minusDays(19 - i));
            b.setReadyDate(bs[2].equals("READY") || bs[2].equals("BOTTLED") || bs[2].equals("SOLD_OUT") ? today.minusDays(10) : null);
            b.setMfgDate(today.minusDays(12));
            b.setBestBeforeDate(today.plusMonths(6));
            b.setStatus(bs[2]);
            b.setNotes("Demo batch");
            b.setCreatedAt(now.minusDays(20 - i));
            b.setUpdatedAt(now);
            Batch saved = batches.save(b);

            BatchCost cost = new BatchCost();
            cost.setBatch(saved);
            cost.setShopId(shopId);
            cost.setSeedCost(saved.getSeedCost());
            cost.setElectricityCost(new BigDecimal("1800"));
            cost.setLabourCost(new BigDecimal("2400"));
            cost.setOtherCost(new BigDecimal("600"));
            cost.setKhaliCredit(new BigDecimal("4000"));
            cost.setTotalCost(cost.getSeedCost().add(cost.getElectricityCost()).add(cost.getLabourCost()).add(cost.getOtherCost()).subtract(cost.getKhaliCredit()));
            cost.setCostPerLitre(cost.getTotalCost().divide(saved.getOilLitres(), 2, RoundingMode.HALF_UP));
            cost.setManualOverride(false);
            cost.setComputedAt(now);
            cost.setCreatedAt(now.minusDays(20 - i));
            cost.setUpdatedAt(now);
            batchCosts.save(cost);

            BatchQualityNote qn = new BatchQualityNote();
            qn.setBatch(saved);
            qn.setNote("Colour golden, no sediment");
            qn.setCheckedBy("Ramesh");
            qn.setQualityLevel("GOOD");
            qn.setCreatedAt(now.minusDays(18 - i));
            qualityNotes.save(qn);
        }

        // ── Expenses ──────────────────────────────────────────────────────────
        String[][] exp = {
                {"Electricity bill", "18500", "1"}, {"Loader wages", "4200", "2"},
                {"Shop rent", "12000", "3"}, {"Diesel for press", "9800", "4"},
                {"Bottle caps", "5200", "5"}, {"Press repair", "7600", "6"},
                {"Lab test", "2500", "7"}, {"FSSAI fee", "3000", "8"},
                {"Misc", "1500", "9"}, {"Electricity bill", "17900", "1"},
                {"Diesel for press", "10400", "4"}, {"Labour salary", "24000", "2"},
                {"Bottle caps", "6000", "5"}, {"Transport", "4300", "4"}, {"Shop rent", "12000", "3"},
        };
        for (int i = 0; i < exp.length; i++) {
            Expense e = new Expense();
            e.setShopId(shopId);
            e.setCategoryId(Long.valueOf(exp[i][2]));
            e.setAmount(new BigDecimal(exp[i][1]));
            e.setTitle(exp[i][0]);
            e.setExpenseDate(today.minusDays(rnd.nextInt(90)));
            e.setPaymentMode("CASH");
            e.setCreatedBy(ownerId);
            e.setCreatedAt(now.minusDays(rnd.nextInt(60)));
            e.setUpdatedAt(now);
            expenses.save(e);
            db.jdbc().update("""
                    INSERT INTO payments (shop_id, direction, mode, amount, source_type, source_id,
                                          reference_no, payment_date, note, created_by, created_at, updated_at)
                    VALUES (:shop, 'OUT', :mode, :amount, 'EXPENSE', :sourceId, NULL, :date, :note, :user, :now, :now)""",
                    new MapSqlParameterSource()
                            .addValue("shop", shopId)
                            .addValue("mode", e.getPaymentMode())
                            .addValue("amount", e.getAmount())
                            .addValue("sourceId", e.getId())
                            .addValue("date", java.sql.Date.valueOf(e.getExpenseDate()))
                            .addValue("note", "Expense: " + e.getTitle())
                            .addValue("user", ownerId)
                            .addValue("now", e.getCreatedAt()));
        }

        // ── Returns ───────────────────────────────────────────────────────────
        List<Sale> sampleSales = sales.findAll().stream().limit(4).toList();
        Return.ComplaintType[] types = {Return.ComplaintType.LEAKAGE, Return.ComplaintType.QTY_SHORT,
                Return.ComplaintType.BROKEN, Return.ComplaintType.TASTE_SMELL};
        for (int i = 0; i < 4 && i < sampleSales.size(); i++) {
            Sale s = sampleSales.get(i);
            Return r = new Return();
            r.setShopId(shopId);
            r.setReturnNo("RET-" + String.format("%04d", 5000 + i));
            r.setSourceType(Return.SourceType.SALE);
            r.setSourceId(s.getId());
            r.setCustomerId(s.getCustomer() != null ? s.getCustomer().getId() : custs.get(0).getId());
            r.setType(types[i]);
            r.setStatus(i % 2 == 0 ? Return.Status.OPEN : Return.Status.IN_REVIEW);
            r.setDescription("Demo complaint");
            r.setReportedBy(staffId);
            r.setCreatedAt(now.minusDays(2 + i));
            r.setUpdatedAt(now);
            Return savedR = returns.save(r);
            ReturnItem ri = new ReturnItem();
            ri.setReturnRequest(savedR);
            ri.setShopId(shopId);
            ri.setProduct(prods.get(0));
            ri.setBatchId("BATCH-1000");
            ri.setQty(BigDecimal.ONE);
            ri.setUnitPrice(prods.get(0).getSellingPrice());
            ri.setAction(i % 2 == 0 ? ReturnItem.Action.RESTOCK : ReturnItem.Action.WRITE_OFF);
            ri.setCreatedAt(now.minusDays(2 + i));
            returnItems.save(ri);
        }

        // ── Licences (JDBC, matching LicenceService conventions) ─────────────
        insertLicence(shopId, ownerId, "FSSAI-12345678901234", "FSSAI", today.plusDays(200), "23ABCDE1234F1Z5", now);
        insertLicence(shopId, ownerId, "GST-23ABCDE1234F1Z5", "GST", today.plusDays(15), "23ABCDE1234F1Z5", now);
        insertLicence(shopId, ownerId, "TRADE-MH-4545", "TRADE", today.minusDays(30), null, now);
        insertLicence(shopId, ownerId, "FIRE-NOC-7788", "OTHER", today.plusDays(400), null, now);

        log.info("""
                ----------------------------------------------------------------------
                DEMO DATA SEEDED  --  {}
                  Owner login : phone 9876501234 / password Owner@2026
                  Staff login : phone 9812345670 / password Staff@2026
                ----------------------------------------------------------------------""", DEMO_SHOP_NAME);
    }

    private User user(Long shopId, String name, String phone, String username, Role role, String rawPassword) {
        User u = new User();
        u.setShopId(shopId);
        u.setName(name);
        u.setPhone(phone);
        u.setEmail(username + "@ghanadash.local");
        u.setPassword(passwordEncoder.encode(rawPassword));
        u.setRole(role);
        u.setActive(true);
        u.setCreatedAt(LocalDateTime.now());
        u.setUpdatedAt(LocalDateTime.now());
        return u;
    }

    private Supplier supplier(Long shopId, String name, String phone, String address) {
        Supplier s = new Supplier();
        s.setShopId(shopId);
        s.setName(name);
        s.setPhone(phone);
        s.setAddress(address);
        s.setActive(true);
        s.setCreatedAt(LocalDateTime.now());
        s.setUpdatedAt(LocalDateTime.now());
        return s;
    }

    private PackagingItem packagingItem(Long shopId, String name, PackagingItem.PackagingType type, String size) {
        PackagingItem p = new PackagingItem();
        p.setShopId(shopId);
        p.setName(name);
        p.setType(type);
        p.setSize(size);
        p.setStockQty(BigDecimal.ZERO);
        p.setCostPerUnit(new BigDecimal("18"));
        p.setLowThreshold(BigDecimal.ZERO);
        p.setActive(true);
        p.setCreatedAt(LocalDateTime.now());
        p.setUpdatedAt(LocalDateTime.now());
        return p;
    }

    private SupplierPayment supplierPayment(Long shopId, Long supplierId, String amount, Long ownerId) {
        SupplierPayment sp = new SupplierPayment();
        sp.setShopId(shopId);
        sp.setSupplierId(supplierId);
        sp.setAmount(new BigDecimal(amount));
        sp.setMode(Payment.PaymentMode.CASH);
        sp.setPaymentDate(LocalDate.now().minusDays(7));
        sp.setCreatedBy(ownerId);
        sp.setCreatedAt(LocalDateTime.now());
        sp.setUpdatedAt(LocalDateTime.now());
        return sp;
    }

    private void insertLicence(Long shopId, Long ownerId, String number, String type,
                               LocalDate expiry, String fssaiGstin, LocalDateTime now) {
        Map<String, Object> row = new java.util.LinkedHashMap<>();
        row.put("shop_id", shopId);
        row.put("licence_number", number);
        row.put("type", type);
        row.put("issuer", "MP Govt");
        row.put("issue_date", expiry.minusYears(1));
        row.put("expiry_date", expiry);
        row.put("fssai_gstin", fssaiGstin);
        row.put("status", licenceStatus(expiry));
        row.put("created_by", ownerId);
        row.put("created_at", now);
        row.put("updated_at", now);
        db.insert("licences", row);
    }

    private String licenceStatus(LocalDate expiry) {
        long days = LocalDate.now().until(expiry).getDays();
        if (days < 0) {
            return "EXPIRED";
        }
        if (days <= 30) {
            return "EXPIRING_SOON";
        }
        return "ACTIVE";
    }
}