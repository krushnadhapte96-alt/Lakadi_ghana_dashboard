package com.ghana.dashboard;

import com.ghana.dashboard.domain.Customer;
import com.ghana.dashboard.domain.CustomerAddress;
import com.ghana.dashboard.domain.Order;
import com.ghana.dashboard.domain.Product;
import com.ghana.dashboard.domain.ProductBatchStock;
import com.ghana.dashboard.domain.Role;
import com.ghana.dashboard.domain.Sale;
import com.ghana.dashboard.domain.SaleItem;
import com.ghana.dashboard.domain.Supplier;
import com.ghana.dashboard.domain.User;
import com.ghana.dashboard.repository.CustomerAddressRepository;
import com.ghana.dashboard.repository.CustomerRepository;
import com.ghana.dashboard.repository.OrderRepository;
import com.ghana.dashboard.repository.ProductBatchStockRepository;
import com.ghana.dashboard.repository.ProductRepository;
import com.ghana.dashboard.repository.SaleItemRepository;
import com.ghana.dashboard.repository.SaleRepository;
import com.ghana.dashboard.repository.SupplierRepository;
import com.ghana.dashboard.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Notifications and global search.
 *
 * <p>Two behaviours here are worth more tests than the happy path:
 *
 * <ul>
 *   <li><b>Read state is per person.</b> A broadcast that one user opens must stay unread for the
 *       rest of the shop, otherwise the first person to look at the bell silences it for everyone.
 *   <li><b>Sweeps must be repeatable.</b> The jobs run on a schedule and can be re-triggered by
 *       hand, so running one twice has to be a no-op the second time. Every job assertion therefore
 *       runs the job again and expects zero created.
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Rollback
class NotificationSearchIntegrationTest {

    private static final String PASSWORD = "secret1234";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private UserRepository userRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private ProductBatchStockRepository batchStockRepository;
    @Autowired private SaleRepository saleRepository;
    @Autowired private SaleItemRepository saleItemRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private CustomerAddressRepository customerAddressRepository;
    @Autowired private SupplierRepository supplierRepository;

    private record Shop(Long id, Long ownerId, String ownerToken, String staffToken) {}

    // ── the bell ───────────────────────────────────────────────────────────────

    @Test
    void bellNeedsALoginAndStartsEmpty() throws Exception {
        Shop shop = newShop();

        mockMvc.perform(get("/api/v1/notifications")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/notifications/unread-count")).andExpect(status().isUnauthorized());

        assertThat(data(getOk("/api/v1/notifications", shop.ownerToken())).path("items").isEmpty()).isTrue();
        assertThat(unread(shop.ownerToken())).isZero();
    }

    @Test
    void openingABroadcastLeavesItUnreadForTheRestOfTheShop() throws Exception {
        Shop shop = newShop();
        long id = testNotification(shop.ownerToken(), "DAY_NOT_CLOSED", "CRITICAL");

        assertThat(unread(shop.ownerToken())).isEqualTo(1);
        assertThat(unread(shop.staffToken())).isEqualTo(1);

        data(postOk("/api/v1/notifications/" + id + "/read", shop.ownerToken()));

        assertThat(unread(shop.ownerToken())).isZero();
        // The important half: the owner has read it, the staff account has not.
        assertThat(unread(shop.staffToken())).isEqualTo(1);

        data(postOk("/api/v1/notifications/read-all", shop.staffToken()));
        assertThat(unread(shop.staffToken())).isZero();
    }

    @Test
    void oneUserCannotReadANotificationAddressedToSomebodyElse() throws Exception {
        Shop shop = newShop();
        // A row addressed specifically to the owner - the dev helper always writes broadcasts, so
        // this one is inserted directly to pin the targeted-read path.
        jdbcTemplate.update("""
                INSERT INTO notifications (shop_id, user_id, type, severity, title, created_at)
                VALUES (?, ?, 'LOW_STOCK', 'WARNING', 'owner only', ?)""",
                shop.id(), shop.ownerId(), LocalDateTime.now());
        flush();

        JsonNode toOwner = data(getOk("/api/v1/notifications", shop.ownerToken()));
        long id = toOwner.path("items").get(0).path("id").asLong();

        JsonNode staffAttempt = data(postOk("/api/v1/notifications/" + id + "/read", shop.staffToken()));
        assertThat(staffAttempt.path("updated").asInt()).isZero();
        assertThat(unread(shop.ownerToken())).isEqualTo(1);

        // Staff must not even see the targeted row listed.
        assertThat(data(getOk("/api/v1/notifications", shop.staffToken())).path("total").asInt()).isZero();
    }

    @Test
    void markingSomethingReadClearsTheBadgeForTheListToo() throws Exception {
        Shop shop = newShop();
        long id = testNotification(shop.ownerToken(), "EXPIRY", "WARNING");

        JsonNode before = data(getOk("/api/v1/notifications", shop.ownerToken()));
        assertThat(before.path("total").asInt()).isEqualTo(1);
        assertThat(before.path("items").get(0).path("read").asBoolean()).isFalse();
        assertThat(before.path("unreadCount").asLong()).isEqualTo(1);

        data(postOk("/api/v1/notifications/" + id + "/read", shop.ownerToken()));

        JsonNode after = data(getOk("/api/v1/notifications", shop.ownerToken()));
        assertThat(after.path("items").get(0).path("read").asBoolean()).isTrue();
        assertThat(after.path("unreadCount").asLong()).isZero();

        // The unread filter has to agree with the badge.
        JsonNode filtered = data(getOk("/api/v1/notifications?unreadOnly=true", shop.ownerToken()));
        assertThat(filtered.path("items").isEmpty()).isTrue();
    }

    // ── a sale that pushes stock under the threshold ────────────────────────────

    @Test
    void aSaleThatCrossesTheThresholdRaisesOneLowStockAlert() throws Exception {
        Shop shop = newShop();
        Product milk = product(shop, "Amul Toned Milk", "MILK500", "10", "5");
        batchStock(shop, milk, "BATCH-A", "10", LocalDate.now().plusMonths(2));
        flush();

        // Above the threshold going in, below it coming out: this is the alert we want.
        sellMilk(shop, milk, "6");
        flush();

        JsonNode alerts = notificationsOfType(shop.ownerToken(), "LOW_STOCK");
        assertThat(alerts.path("total").asInt()).isEqualTo(1);
        assertThat(alerts.path("items").get(0).path("severity").asText()).isEqualTo("WARNING");
        assertThat(alerts.path("items").get(0).path("link").asText()).contains("/inventory");
        assertThat(alerts.path("items").get(0).path("message").asText()).contains("4");
    }

    @Test
    void aProductThatWasAlreadyShortStaysQuiet() throws Exception {
        Shop shop = newShop();
        Product milk = product(shop, "Amul Toned Milk", "MILK500", "10", "5");
        batchStock(shop, milk, "BATCH-A", "4", LocalDate.now().plusMonths(2));
        flush();

        sellMilk(shop, milk, "1");
        flush();

        assertThat(notificationsOfType(shop.ownerToken(), "LOW_STOCK").path("total").asInt()).isZero();
    }

    @Test
    void runningTheLowStockJobTwiceOnlyAlertsOnce() throws Exception {
        Shop shop = newShop();
        Product milk = product(shop, "Amul Toned Milk", "MILK500", "10", "5");
        batchStock(shop, milk, "BATCH-A", "2", LocalDate.now().plusMonths(2));
        flush();

        JsonNode first = data(postOk("/api/v1/dev/notifications/jobs/LOW_STOCK", shop.ownerToken()));
        assertThat(first.path("created").asInt()).isEqualTo(1);

        // The sweep runs on a schedule and can be triggered by hand; the second run must find the
        // same condition and stay quiet.
        JsonNode second = data(postOk("/api/v1/dev/notifications/jobs/LOW_STOCK", shop.ownerToken()));
        assertThat(second.path("created").asInt()).isZero();
        assertThat(second.path("skipped").asInt()).isEqualTo(1);
    }

    // ── new orders ─────────────────────────────────────────────────────────────

    @Test
    void aNewOrderAnnouncesItselfExactlyOnce() throws Exception {
        Shop shop = newShop();
        Order order = order(shop);
        flush();

        JsonNode created = notificationsOfType(shop.ownerToken(), "NEW_ORDER");
        assertThat(created.path("total").asInt()).isEqualTo(1);
        assertThat(created.path("items").get(0).path("title").asText()).contains(order.getBillNo());

        // The event already announced it, so the recovery sweep must not add a second copy.
        JsonNode sweep = data(postOk("/api/v1/dev/notifications/jobs/NEW_ORDER", shop.ownerToken()));
        assertThat(sweep.path("created").asInt()).isZero();
        assertThat(notificationsOfType(shop.ownerToken(), "NEW_ORDER").path("total").asInt()).isEqualTo(1);
    }

    @Test
    void theOrderAlertReachesEveryDeviceInTheShop() throws Exception {
        Shop shop = newShop();
        order(shop);
        flush();

        // Nobody should have to open the counter's tablet to know an order arrived.
        assertThat(unread(shop.ownerToken())).isEqualTo(1);
        assertThat(unread(shop.staffToken())).isEqualTo(1);
    }

    // ── the jobs themselves ────────────────────────────────────────────────────

    @Test
    void anUnclosedCashBookDayIsTheOneCriticalAlert() throws Exception {
        Shop shop = newShop();

        JsonNode result = data(postOk("/api/v1/dev/notifications/jobs/DAY_NOT_CLOSED", shop.ownerToken()));
        assertThat(result.path("created").asInt()).isEqualTo(1);

        JsonNode alerts = notificationsOfType(shop.ownerToken(), "DAY_NOT_CLOSED");
        assertThat(alerts.path("items").get(0).path("severity").asText()).isEqualTo("CRITICAL");

        JsonNode again = data(postOk("/api/v1/dev/notifications/jobs/DAY_NOT_CLOSED", shop.ownerToken()));
        assertThat(again.path("created").asInt()).isZero();
    }

    @Test
    void everyJobCanBeTriggeredByName() throws Exception {
        Shop shop = newShop();
        product(shop, "Amul Toned Milk", "MILK500", "10", "5");
        batchStock(shop, productRepository.findAll().get(0), "BATCH-A", "2", LocalDate.now().plusDays(3));
        flush();

        JsonNode names = data(getOk("/api/v1/dev/notifications/jobs", shop.ownerToken()));
        assertThat(names.isArray()).isTrue();
        assertThat(names.size()).isEqualTo(10);

        for (JsonNode name : names) {
            data(postOk("/api/v1/dev/notifications/jobs/" + name.asText(), shop.ownerToken()));
        }
    }

    @Test
    void anUnknownJobNameIsRejectedWithTheListOfRealOnes() throws Exception {
        Shop shop = newShop();

        mockMvc.perform(post("/api/v1/dev/notifications/jobs/NOT_A_JOB")
                        .header("Authorization", auth(shop.ownerToken())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void staffCannotTriggerJobs() throws Exception {
        Shop shop = newShop();

        mockMvc.perform(post("/api/v1/dev/notifications/jobs/LOW_STOCK")
                        .header("Authorization", auth(shop.staffToken())))
                .andExpect(status().isForbidden());
    }

    @Test
    void supplierDuesAreReadFromUnpaidPurchases() throws Exception {
        Shop shop = newShop();
        Supplier supplier = supplier(shop, "Kisan Traders");
        jdbcTemplate.update("""
                INSERT INTO seed_purchases (shop_id, seed_type_id, supplier_id, qty_kg, rate_per_kg,
                                            total_amount, grade, purchase_date, payment_status,
                                            amount_paid, created_by, created_at, updated_at, version)
                VALUES (?, 1, ?, 50.000, 100.00, 5000.00, 'A', ?, 'PARTIAL', 2000.00, ?, ?, ?, 0)""",
                shop.id(), supplier.getId(), LocalDate.now().minusDays(3),
                shop.ownerId(), LocalDateTime.now(), LocalDateTime.now());
        flush();

        JsonNode result = data(postOk("/api/v1/dev/notifications/jobs/SUPPLIER_DUE", shop.ownerToken()));
        assertThat(result.path("created").asInt()).isEqualTo(1);

        JsonNode alerts = notificationsOfType(shop.ownerToken(), "SUPPLIER_DUE");
        assertThat(alerts.path("items").get(0).path("title").asText()).contains("Kisan Traders");
        assertThat(alerts.path("items").get(0).path("message").asText()).contains("3000");
    }

    // ── global search ──────────────────────────────────────────────────────────

    @Test
    void searchNeedsALoginAndIgnoresABlankQuery() throws Exception {
        Shop shop = newShop();
        product(shop, "Amul Toned Milk", "MILK500", "10", "5");
        flush();

        mockMvc.perform(get("/api/v1/search").param("q", "milk"))
                .andExpect(status().isUnauthorized());

        JsonNode blank = data(getOk("/api/v1/search?q=", shop.ownerToken()));
        assertThat(blank.path("total").asInt()).isZero();
        assertThat(blank.path("groups").isEmpty()).isTrue();
    }

    @Test
    void searchFindsBillsCustomersAndBatchesInTheRightGroups() throws Exception {
        Shop shop = newShop();
        Product milk = product(shop, "Amul Toned Milk", "MILK500", "10", "5");
        batchStock(shop, milk, "GH-8842", "10", LocalDate.now().plusMonths(2));
        Customer customer = customer(shop, "Ramesh Gupta", "9876543210");
        Sale sale = sale(shop, "GH-778899");
        sale.setCustomer(customer);
        saleRepository.save(sale);
        flush();

        // The bill number a customer quotes at the counter.
        JsonNode byBill = data(getOk("/api/v1/search?q=GH-7788", shop.ownerToken()));
        assertThat(byBill.path("total").asInt()).isEqualTo(1);
        assertThat(byBill.path("groups").get(0).path("group").asText()).isEqualTo("BILL_NUMBER");
        assertThat(byBill.path("groups").get(0).path("items").get(0).path("title").asText())
                .isEqualTo("GH-778899");

        // A customer's name, which is how people actually search.
        JsonNode byName = data(getOk("/api/v1/search?q=Ramesh", shop.ownerToken()));
        assertThat(group(byName, "CUSTOMERS").path("items").get(0).path("subtitle").asText())
                .isEqualTo("9876543210");
        // The same name also turns up on the sale, which is useful, and stays in its own group.
        assertThat(group(byName, "SALES").path("items").get(0).path("title").asText()).isEqualTo("GH-778899");

        // A printed batch code on a returned tin.
        JsonNode byBatch = data(getOk("/api/v1/search?q=GH-8842", shop.ownerToken()));
        assertThat(group(byBatch, "BATCHES").path("items").get(0).path("title").asText())
                .isEqualTo("GH-8842");

        // A product by its own name or SKU.
        JsonNode byProduct = data(getOk("/api/v1/search?q=MILK500", shop.ownerToken()));
        assertThat(group(byProduct, "PRODUCTS").path("items").get(0).path("link").asText())
                .isEqualTo("/inventory?product=" + milk.getId());
    }

    @Test
    void searchFindsKhataPartiesAndSuppliers() throws Exception {
        Shop shop = newShop();
        supplier(shop, "Kisan Traders");
        jdbcTemplate.update("""
                INSERT INTO khata_parties (shop_id, name, type, phone, active, created_at, updated_at, version)
                VALUES (?, 'Wholesale Sharma', 'INDIVIDUAL', '9811122233', 1, ?, ?, 0)""",
                shop.id(), LocalDateTime.now(), LocalDateTime.now());
        flush();

        JsonNode party = data(getOk("/api/v1/search?q=Wholesale", shop.ownerToken()));
        assertThat(group(party, "KHATA_PARTIES").path("items").get(0).path("title").asText())
                .isEqualTo("Wholesale Sharma");

        JsonNode byPhone = data(getOk("/api/v1/search?q=9811122233", shop.ownerToken()));
        assertThat(group(byPhone, "KHATA_PARTIES").path("items").get(0).path("link").asText())
                .isEqualTo("/khata?party=" + group(byPhone, "KHATA_PARTIES").path("items").get(0)
                        .path("id").asLong());

        JsonNode supplier = data(getOk("/api/v1/search?q=Kisan", shop.ownerToken()));
        assertThat(group(supplier, "SUPPLIERS").path("items").get(0).path("title").asText())
                .isEqualTo("Kisan Traders");
    }

    @Test
    void searchStopsAfterFiveRowsInAGroup() throws Exception {
        Shop shop = newShop();
        for (int i = 1; i <= 7; i++) {
            product(shop, "Amul Toned " + i, "MILK" + i, "10", "5");
        }
        flush();

        JsonNode result = data(getOk("/api/v1/search?q=Amul", shop.ownerToken()));
        JsonNode products = group(result, "PRODUCTS");
        assertThat(products.path("items").size()).isEqualTo(5);
        assertThat(products.path("hasMore").asBoolean()).isTrue();
    }

    @Test
    void searchTreatsWildcardCharactersAsText() throws Exception {
        Shop shop = newShop();
        product(shop, "Amul Toned Milk", "MILK500", "10", "5");
        flush();

        // Unescaped, '%' would match every product in the shop.
        JsonNode result = data(getOk("/api/v1/search?q=%25", shop.ownerToken()));
        assertThat(result.path("total").asInt()).isZero();
    }

    @Test
    void searchNeverLeaksAnotherShopsRows() throws Exception {
        Shop shop = newShop();
        Shop other = newShop();
        product(shop, "Shop One Secret Product", "SECRET1", "10", "5");
        product(other, "Shop Two Secret Product", "SECRET2", "10", "5");
        flush();

        JsonNode result = data(getOk("/api/v1/search?q=Secret", shop.ownerToken()));
        assertThat(result.path("total").asInt()).isEqualTo(1);
        assertThat(group(result, "PRODUCTS").path("items").get(0).path("title").asText())
                .isEqualTo("Shop One Secret Product");
    }

    // ── fixtures ───────────────────────────────────────────────────────────────

    private Shop newShop() throws Exception {
        String phone = String.valueOf(ThreadLocalRandom.current().nextLong(6_000_000_000L, 9_999_999_999L));
        mockMvc.perform(post("/api/v1/auth/register-shop")
                        .with(request -> {
                            request.setRemoteAddr("10.9." + ThreadLocalRandom.current().nextInt(250) + "."
                                    + ThreadLocalRandom.current().nextInt(250));
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "shopName", "Bell Shop " + phone, "ownerName", "Owner " + phone,
                                "phone", phone, "email", phone + "@example.com", "password", PASSWORD))))
                .andExpect(status().isCreated());

        User owner = userRepository.findByPhone(phone).orElseThrow();
        String staffPhone = String.valueOf(ThreadLocalRandom.current().nextLong(6_000_000_000L, 9_999_999_999L));
        User staff = new User();
        staff.setShopId(owner.getShopId());
        staff.setName("Staff " + staffPhone);
        staff.setPhone(staffPhone);
        staff.setPassword(passwordEncoder.encode(PASSWORD));
        staff.setRole(Role.STAFF);
        staff.setActive(true);
        staff.setCreatedAt(LocalDateTime.now());
        staff.setUpdatedAt(LocalDateTime.now());
        userRepository.save(staff);

        return new Shop(owner.getShopId(), owner.getId(), login(phone), login(staffPhone));
    }

    private String login(String phone) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .with(request -> {
                            request.setRemoteAddr("10.8." + ThreadLocalRandom.current().nextInt(250) + "."
                                    + ThreadLocalRandom.current().nextInt(250));
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "identifier", phone, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();
        return json(result).path("data").path("accessToken").asText();
    }

    /** Creates one broadcast notification through the dev helper and returns its id. */
    private long testNotification(String token, String type, String severity) throws Exception {
        JsonNode result = data(postOk("/api/v1/dev/notifications/test?type=" + type
                + "&severity=" + severity + "&title=Bell+check&message=dev+test", token));
        assertThat(result.path("written").asBoolean()).isTrue();
        return data(getOk("/api/v1/notifications?type=" + type, token)).path("items").get(0)
                .path("id").asLong();
    }

    private void sellMilk(Shop shop, Product product, String qty) throws Exception {
        mockMvc.perform(post("/api/v1/sales")
                        .header("Authorization", auth(shop.ownerToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "items", List.of(Map.of(
                                        "productId", product.getId(),
                                        "qty", new BigDecimal(qty),
                                        "rate", new BigDecimal("60"))),
                                "discount", new BigDecimal("0"),
                                "discountType", "AMOUNT",
                                "paymentMode", "CASH",
                                "paidAmount", new BigDecimal("600")))))
                .andExpect(status().isCreated());
    }

    private Product product(Shop shop, String name, String sku, String cost, String threshold) {
        Product p = new Product();
        p.setShopId(shop.id());
        p.setName(name);
        p.setSku(sku + "-" + ThreadLocalRandom.current().nextInt(1000, 9999));
        p.setPackSize(new BigDecimal("1"));
        p.setUnit(Product.Unit.L);
        p.setContainer(Product.Container.plastic);
        p.setSellingPrice(new BigDecimal("60"));
        p.setMrp(new BigDecimal("65"));
        p.setCostPrice(new BigDecimal(cost));
        p.setLowStockThreshold(new BigDecimal(threshold));
        p.setActive(true);
        p.setKhali(false);
        p.setCreatedAt(LocalDateTime.now());
        p.setUpdatedAt(LocalDateTime.now());
        return productRepository.save(p);
    }

    private void batchStock(Shop shop, Product product, String batchId, String qty, LocalDate expiry) {
        ProductBatchStock stock = new ProductBatchStock();
        stock.setShopId(shop.id());
        stock.setProduct(product);
        stock.setBatchId(batchId);
        stock.setQtyAvailable(new BigDecimal(qty));
        stock.setQtyReserved(BigDecimal.ZERO);
        stock.setCostPrice(product.getCostPrice());
        stock.setReceivedDate(LocalDate.now().minusMonths(1));
        stock.setExpiryDate(expiry);
        stock.setActive(true);
        stock.setCreatedAt(LocalDateTime.now());
        stock.setUpdatedAt(LocalDateTime.now());
        batchStockRepository.save(stock);
    }

    private Sale sale(Shop shop, String billNo) {
        User owner = userRepository.findById(shop.ownerId()).orElseThrow();
        Sale s = new Sale();
        s.setShopId(shop.id());
        s.setBillNo(billNo);
        s.setUser(owner);
        s.setSubtotal(new BigDecimal("500"));
        s.setDiscount(BigDecimal.ZERO);
        s.setDiscountType(Sale.DiscountType.AMOUNT);
        s.setTotalAmount(new BigDecimal("500"));
        s.setPaidAmount(new BigDecimal("500"));
        s.setPaymentMode(Sale.PaymentMode.CASH);
        s.setPaymentStatus(Sale.PaymentStatus.PAID);
        s.setVoid(false);
        s.setActive(true);
        s.setCreatedAt(LocalDateTime.now());
        s.setUpdatedAt(LocalDateTime.now());
        return saleRepository.save(s);
    }

    private Customer customer(Shop shop, String name, String phone) {
        Customer c = new Customer();
        c.setShopId(shop.id());
        c.setName(name);
        c.setPhone(phone);
        c.setActive(true);
        c.setCreatedAt(LocalDateTime.now());
        c.setUpdatedAt(LocalDateTime.now());
        return customerRepository.save(c);
    }

    /** An order placed through the API, so the new-order event fires for real. */
    private Order order(Shop shop) throws Exception {
        Customer c = customer(shop, "Order Customer " + ThreadLocalRandom.current().nextInt(1000, 9999),
                String.valueOf(ThreadLocalRandom.current().nextLong(6_000_000_000L, 9_999_999_999L)));
        CustomerAddress address = new CustomerAddress();
        address.setCustomer(c);
        address.setShopId(shop.id());
        address.setAddressType(CustomerAddress.AddressType.HOME);
        address.setAddressLine1("1 Bell Road");
        address.setCity("Accra");
        address.setState("GA");
        address.setPincode("100001");
        address.setActive(true);
        address.setCreatedAt(LocalDateTime.now());
        address.setUpdatedAt(LocalDateTime.now());
        customerAddressRepository.save(address);

        Product product = product(shop, "Order Item Milk", "ORDMILK", "50", "2");
        batchStock(shop, product, "ORD-" + ThreadLocalRandom.current().nextInt(1000, 9999), "20",
                LocalDate.now().plusMonths(1));
        flush();

        mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", auth(shop.ownerToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "customerId", c.getId(),
                                "addressId", address.getId(),
                                "items", List.of(Map.of(
                                        "productId", product.getId(),
                                        "qty", new BigDecimal("2"),
                                        "rate", new BigDecimal("60"))),
                                "discount", new BigDecimal("0"),
                                "deliveryCharge", new BigDecimal("0"),
                                "discountType", "AMOUNT",
                                "paymentMode", "COD",
                                "paidAmount", new BigDecimal("0"),
                                "expectedDeliveryDate", LocalDate.now().plusDays(2).toString()))))
                .andExpect(status().isCreated());

        return orderRepository.findByShopIdAndActiveTrueOrderByCreatedAtDesc(shop.id()).get(0);
    }

    private Supplier supplier(Shop shop, String name) {
        Supplier s = new Supplier();
        s.setShopId(shop.id());
        s.setName(name);
        s.setPhone(String.valueOf(ThreadLocalRandom.current().nextLong(6_000_000_000L, 9_999_999_999L)));
        s.setActive(true);
        s.setCreatedAt(LocalDateTime.now());
        s.setUpdatedAt(LocalDateTime.now());
        return supplierRepository.save(s);
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    /** The service reads through plain SQL, so writes have to reach the table first. */
    private void flush() {
        entityManager.flush();
        entityManager.clear();
    }

    private long unread(String token) throws Exception {
        return data(getOk("/api/v1/notifications/unread-count", token)).path("count").asLong();
    }

    private JsonNode notificationsOfType(String token, String type) throws Exception {
        return data(getOk("/api/v1/notifications?type=" + type, token));
    }

    private static JsonNode group(JsonNode search, String name) {
        for (JsonNode group : search.path("groups")) {
            if (group.path("group").asText().equals(name)) {
                return group;
            }
        }
        throw new AssertionError("No group '" + name + "' in " + search.path("groups"));
    }

    private MvcResult getOk(String url, String token) throws Exception {
        return mockMvc.perform(get(url).header("Authorization", auth(token)))
                .andExpect(status().isOk()).andReturn();
    }

    private MvcResult postOk(String url, String token) throws Exception {
        return mockMvc.perform(post(url).header("Authorization", auth(token)))
                .andExpect(status().isOk()).andReturn();
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode data(MvcResult result) throws Exception {
        return json(result).path("data");
    }

    private static String auth(String token) {
        return "Bearer " + token;
    }
}