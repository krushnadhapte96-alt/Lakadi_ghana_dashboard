package com.ghana.dashboard;

import com.ghana.dashboard.domain.CashBookDay;
import com.ghana.dashboard.domain.Customer;
import com.ghana.dashboard.domain.CustomerAddress;
import com.ghana.dashboard.domain.LossLedger;
import com.ghana.dashboard.domain.Order;
import com.ghana.dashboard.domain.OrderItem;
import com.ghana.dashboard.domain.Payment;
import com.ghana.dashboard.domain.Product;
import com.ghana.dashboard.domain.Role;
import com.ghana.dashboard.domain.Sale;
import com.ghana.dashboard.domain.SaleItem;
import com.ghana.dashboard.domain.User;
import com.ghana.dashboard.repository.CashBookDayRepository;
import com.ghana.dashboard.repository.CustomerAddressRepository;
import com.ghana.dashboard.repository.CustomerRepository;
import com.ghana.dashboard.repository.LossLedgerRepository;
import com.ghana.dashboard.repository.OilTypeRepository;
import com.ghana.dashboard.repository.OrderItemRepository;
import com.ghana.dashboard.repository.OrderRepository;
import com.ghana.dashboard.repository.PaymentRepository;
import com.ghana.dashboard.repository.ProductRepository;
import com.ghana.dashboard.repository.SaleItemRepository;
import com.ghana.dashboard.repository.SaleRepository;
import com.ghana.dashboard.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Rollback
class ExpensePnlIntegrationTest {

    private static final String PASSWORD = "secret1234";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private EntityManager entityManager;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private UserRepository userRepository;
    @Autowired private OilTypeRepository oilTypeRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private SaleRepository saleRepository;
    @Autowired private SaleItemRepository saleItemRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private CustomerAddressRepository customerAddressRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private OrderItemRepository orderItemRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private CashBookDayRepository cashBookDayRepository;
    @Autowired private LossLedgerRepository lossLedgerRepository;

    private final List<Long> shopIds = new ArrayList<>();

    @AfterEach
    void removeUploadedBills() throws IOException {
        for (Long shopId : shopIds) {
            Path dir = Paths.get("./uploads/expenses", shopId.toString());
            if (Files.exists(dir)) {
                try (Stream<Path> files = Files.walk(dir)) {
                    files.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
                }
            }
        }
    }

    private record Shop(Long id, Long ownerId, String ownerToken, String staffToken) {}

    private Shop newShop() throws Exception {
        String phone = String.valueOf(ThreadLocalRandom.current().nextLong(6_000_000_000L, 9_999_999_999L));
        mockMvc.perform(post("/api/v1/auth/register-shop")
                        .with(request -> {
                            request.setRemoteAddr("10.1." + ThreadLocalRandom.current().nextInt(250) + "."
                                    + ThreadLocalRandom.current().nextInt(250));
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "shopName", "P8 Shop " + phone, "ownerName", "Owner " + phone,
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

        shopIds.add(owner.getShopId());
        return new Shop(owner.getShopId(), owner.getId(), login(phone), login(staffPhone));
    }

    private String login(String phone) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .with(request -> {
                            request.setRemoteAddr("10.2." + ThreadLocalRandom.current().nextInt(250) + "."
                                    + ThreadLocalRandom.current().nextInt(250));
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("identifier", phone, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();
        return json(result).path("data").path("accessToken").asText();
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private static String auth(String token) {
        return "Bearer " + token;
    }

    private JsonNode categories(String token) throws Exception {
        return json(mockMvc.perform(get("/api/v1/expense-categories").header("Authorization", auth(token)))
                .andExpect(status().isOk()).andReturn()).path("data");
    }

    private MockMultipartHttpServletRequestBuilder expenseForm(HttpMethod method, String url, String token,
                                                               long categoryId, String amount, LocalDate date,
                                                               String mode) {
        return (MockMultipartHttpServletRequestBuilder) MockMvcRequestBuilders.multipart(method, url)
                .param("categoryId", String.valueOf(categoryId))
                .param("amount", amount)
                .param("title", "Test expense")
                .param("expenseDate", date.toString())
                .param("paymentMode", mode)
                .header("Authorization", auth(token));
    }

    private JsonNode createExpense(Shop shop, long categoryId, String amount, LocalDate date, String mode)
            throws Exception {
        return json(mockMvc.perform(expenseForm(HttpMethod.POST, "/api/v1/expenses", shop.ownerToken(),
                        categoryId, amount, date, mode)).andExpect(status().isCreated()).andReturn()).path("data");
    }

    private JsonNode pnl(Shop shop, LocalDate from, LocalDate to, String groupBy) throws Exception {
        return json(mockMvc.perform(get("/api/v1/pnl")
                        .param("from", from.toString()).param("to", to.toString()).param("groupBy", groupBy)
                        .header("Authorization", auth(shop.ownerToken())))
                .andExpect(status().isOk()).andReturn()).path("data");
    }

    private static BigDecimal dec(JsonNode node) {
        return new BigDecimal(node.asText());
    }

    private static void assertMoney(JsonNode actual, String expected) {
        assertThat(dec(actual)).isEqualByComparingTo(expected);
    }

    // ── access control ────────────────────────────────────────────────────────

    @Test
    void staffAndAnonymousAreBlockedFromEveryExpenseAndPnlEndpoint() throws Exception {
        Shop shop = newShop();
        LocalDate today = LocalDate.now();
        String[] urls = {
                "/api/v1/expenses", "/api/v1/expenses/1", "/api/v1/expenses/1/bill", "/api/v1/expenses/by-category",
                "/api/v1/expense-categories", "/api/v1/pnl", "/api/v1/pnl/export.csv",
                "/api/v1/pnl/report.pdf", "/api/v1/loss-ledger"};

        for (String url : urls) {
            mockMvc.perform(get(url).param("from", today.toString()).param("to", today.toString())
                            .header("Authorization", auth(shop.staffToken())))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get(url)).andExpect(status().isUnauthorized());
        }

        mockMvc.perform(expenseForm(HttpMethod.POST, "/api/v1/expenses", shop.staffToken(), 1, "10", today, "CASH"))
                .andExpect(status().isForbidden());
        mockMvc.perform(expenseForm(HttpMethod.PUT, "/api/v1/expenses/1", shop.staffToken(), 1, "10", today, "CASH"))
                .andExpect(status().isForbidden());
        mockMvc.perform(MockMvcRequestBuilders.delete("/api/v1/expenses/1")
                        .header("Authorization", auth(shop.staffToken())))
                .andExpect(status().isForbidden());
    }

    // ── expense -> OUT payment -> net profit ──────────────────────────────────

    @Test
    void expenseCreatesOutPaymentReducesNetAndIsFullyReversedOnDelete() throws Exception {
        Shop shop = newShop();
        LocalDate today = LocalDate.now();
        long categoryId = categories(shop.ownerToken()).get(0).path("id").asLong();
        assertThat(categories(shop.ownerToken())).hasSize(9);

        assertMoney(pnl(shop, today, today, "day").path("netProfit"), "0");

        MockMultipartFile bill = new MockMultipartFile("billFile", "electricity.pdf", "application/pdf",
                "%PDF-1.4 fake".getBytes(StandardCharsets.UTF_8));
        JsonNode created = json(mockMvc.perform(
                        expenseForm(HttpMethod.POST, "/api/v1/expenses", shop.ownerToken(), categoryId, "250.50",
                                today, "UPI").file(bill))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.hasBill").value(true))
                .andReturn()).path("data");
        long expenseId = created.path("id").asLong();

        List<Payment> payments = paymentRepository.findByShopIdAndSourceTypeAndSourceId(
                shop.id(), Payment.SourceType.EXPENSE, expenseId);
        assertThat(payments).hasSize(1);
        assertThat(payments.get(0).getDirection()).isEqualTo(Payment.Direction.OUT);
        assertThat(payments.get(0).getMode()).isEqualTo(Payment.PaymentMode.UPI);
        assertThat(payments.get(0).getAmount()).isEqualByComparingTo("250.50");
        assertThat(cashBookDayRepository.findByShopIdAndDayDate(shop.id(), today).orElseThrow().getTotalOut())
                .isEqualByComparingTo("250.50");

        JsonNode afterCreate = pnl(shop, today, today, "day");
        assertMoney(afterCreate.path("totalExpenses"), "250.50");
        assertMoney(afterCreate.path("netProfit"), "-250.50");

        mockMvc.perform(get("/api/v1/expenses/" + expenseId + "/bill")
                        .header("Authorization", auth(shop.ownerToken())))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(content().string("%PDF-1.4 fake"));

        mockMvc.perform(get("/api/v1/expenses/by-category")
                        .param("dateFrom", today.toString()).param("dateTo", today.toString())
                        .header("Authorization", auth(shop.ownerToken())))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].categoryId").value(categoryId))
                .andExpect(jsonPath("$.data[0].amount").value(250.50));

        mockMvc.perform(MockMvcRequestBuilders.delete("/api/v1/expenses/" + expenseId)
                        .header("Authorization", auth(shop.ownerToken())))
                .andExpect(status().isOk());

        assertThat(paymentRepository.findByShopIdAndSourceTypeAndSourceId(
                shop.id(), Payment.SourceType.EXPENSE, expenseId)).isEmpty();
        assertThat(cashBookDayRepository.findByShopIdAndDayDate(shop.id(), today).orElseThrow().getTotalOut())
                .isEqualByComparingTo("0");
        assertMoney(pnl(shop, today, today, "day").path("netProfit"), "0");
        mockMvc.perform(get("/api/v1/expenses/" + expenseId + "/bill")
                        .header("Authorization", auth(shop.ownerToken())))
                .andExpect(status().isNotFound());
    }

    @Test
    void updatingAnExpenseReplacesItsOutPayment() throws Exception {
        Shop shop = newShop();
        LocalDate today = LocalDate.now();
        long categoryId = categories(shop.ownerToken()).get(0).path("id").asLong();
        long expenseId = createExpense(shop, categoryId, "100", today, "CASH").path("id").asLong();

        mockMvc.perform(expenseForm(HttpMethod.PUT, "/api/v1/expenses/" + expenseId, shop.ownerToken(),
                        categoryId, "175.25", today, "BANK"))
                .andExpect(status().isOk());

        List<Payment> payments = paymentRepository.findByShopIdAndSourceTypeAndSourceId(
                shop.id(), Payment.SourceType.EXPENSE, expenseId);
        assertThat(payments).hasSize(1);
        assertThat(payments.get(0).getAmount()).isEqualByComparingTo("175.25");
        assertThat(payments.get(0).getMode()).isEqualTo(Payment.PaymentMode.BANK);
        assertThat(cashBookDayRepository.findByShopIdAndDayDate(shop.id(), today).orElseThrow().getTotalOut())
                .isEqualByComparingTo("175.25");
        assertMoney(pnl(shop, today, today, "day").path("totalExpenses"), "175.25");
    }

    @Test
    void expenseValidationAndClosedDayRules() throws Exception {
        Shop shop = newShop();
        LocalDate today = LocalDate.now();
        long categoryId = categories(shop.ownerToken()).get(0).path("id").asLong();

        mockMvc.perform(expenseForm(HttpMethod.POST, "/api/v1/expenses", shop.ownerToken(), categoryId, "0", today, "CASH"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(expenseForm(HttpMethod.POST, "/api/v1/expenses", shop.ownerToken(), 999999, "10", today, "CASH"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(expenseForm(HttpMethod.POST, "/api/v1/expenses", shop.ownerToken(), categoryId, "10", today, "CASH")
                        .file(new MockMultipartFile("billFile", "evil.exe", "application/octet-stream", new byte[]{1, 2})))
                .andExpect(status().isBadRequest());

        CashBookDay closed = new CashBookDay();
        closed.setShopId(shop.id());
        closed.setDayDate(today.minusDays(3));
        closed.setStatus(CashBookDay.Status.CLOSED);
        cashBookDayRepository.save(closed);
        mockMvc.perform(expenseForm(HttpMethod.POST, "/api/v1/expenses", shop.ownerToken(), categoryId, "10",
                        today.minusDays(3), "CASH"))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/v1/pnl").param("groupBy", "week").header("Authorization", auth(shop.ownerToken())))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/pnl").param("from", today.toString()).param("to", today.minusDays(1).toString())
                        .header("Authorization", auth(shop.ownerToken())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void otherShopCannotSeeOrTouchAnExpense() throws Exception {
        Shop a = newShop();
        Shop b = newShop();
        LocalDate today = LocalDate.now();
        long categoryId = categories(a.ownerToken()).get(0).path("id").asLong();
        long expenseId = createExpense(a, categoryId, "40", today, "CASH").path("id").asLong();

        mockMvc.perform(get("/api/v1/expenses/" + expenseId).header("Authorization", auth(b.ownerToken())))
                .andExpect(status().isNotFound());
        mockMvc.perform(MockMvcRequestBuilders.delete("/api/v1/expenses/" + expenseId)
                        .header("Authorization", auth(b.ownerToken())))
                .andExpect(status().isNotFound());
        assertMoney(pnl(b, today, today, "day").path("totalExpenses"), "0");
    }

    // ── P&L reconciliation ────────────────────────────────────────────────────

    @Test
    void pnlReconcilesWithSalesDeliveredOrdersKhaliExpensesAndLosses() throws Exception {
        Shop shop = newShop();
        LocalDate today = LocalDate.now();
        User owner = userRepository.findById(shop.ownerId()).orElseThrow();

        Product mustard = product(shop, "MUS-1", "Mustard 1L", "100.00", false,
                oilTypeRepository.findByNameEn("Mustard").orElseThrow());
        Product khali = product(shop, "KHL-1", "Khali 1kg", "30.00", true, null);

        // Sale 1: 5 x 200 = 1000, discount 100 -> total 900, cost 5 x 120 = 600
        Sale sale1 = sale(shop, owner, "1000.00", "900.00", today.atTime(10, 0));
        saleItem(sale1, mustard, "5", "200.00", "1000.00", "120.00");
        // Sale 2: khali 4 x 50 = 200, cost 4 x 30 = 120
        Sale sale2 = sale(shop, owner, "200.00", "200.00", today.atTime(11, 0));
        saleItem(sale2, khali, "4", "50.00", "200.00", "30.00");
        // Void sale must not count
        Sale voided = sale(shop, owner, "999.00", "999.00", today.atTime(12, 0));
        voided.setVoid(true);
        saleRepository.save(voided);
        saleItem(voided, mustard, "1", "999.00", "999.00", "10.00");

        // Delivered order: 2 x 300 = 600 + 50 delivery = 650, cost 2 x 0 -> falls back to product cost 100
        Order delivered = order(shop, owner, "600.00", "650.00", Order.OrderStatus.DELIVERED, today, today.minusDays(1));
        orderItem(delivered, mustard, "2", "300.00", "600.00", "0.00");
        // New (undelivered) order must not count
        Order pending = order(shop, owner, "500.00", "500.00", Order.OrderStatus.NEW, null, today);
        orderItem(pending, mustard, "1", "500.00", "500.00", "100.00");

        loss(shop, owner, mustard, "50.00", today);
        loss(shop, owner, null, "20.00", today);

        entityManager.flush();
        entityManager.clear();

        long categoryId = categories(shop.ownerToken()).get(0).path("id").asLong();
        createExpense(shop, categoryId, "300.00", today, "CASH");
        createExpense(shop, categoryId, "125.50", today, "UPI");

        // revenue = 900 + 200 + 650 = 1750 ; cogs = 600 + 120 + 200 = 920 ; gross = 830
        // expenses = 425.50 ; losses = 70 ; net = 830 - 425.50 - 70 = 334.50
        for (String groupBy : List.of("day", "month", "oilType")) {
            JsonNode pnl = pnl(shop, today.minusDays(2), today, groupBy);
            assertMoney(pnl.path("totalRevenue"), "1750.00");
            assertMoney(pnl.path("totalCogs"), "920.00");
            assertMoney(pnl.path("grossProfit"), "830.00");
            assertMoney(pnl.path("totalExpenses"), "425.50");
            assertMoney(pnl.path("totalLosses"), "70.00");
            assertMoney(pnl.path("netProfit"), "334.50");
            assertThat(pnl.path("grossMarginPercent").asDouble()).isEqualTo(47.43);

            BigDecimal revenue = BigDecimal.ZERO;
            BigDecimal net = BigDecimal.ZERO;
            BigDecimal expenses = BigDecimal.ZERO;
            for (JsonNode g : pnl.path("groups")) {
                revenue = revenue.add(dec(g.path("revenue")));
                expenses = expenses.add(dec(g.path("expenses")));
                net = net.add(dec(g.path("netProfit")));
                assertThat(dec(g.path("netProfit"))).isEqualByComparingTo(
                        dec(g.path("revenue")).subtract(dec(g.path("cogs")))
                                .subtract(dec(g.path("expenses"))).subtract(dec(g.path("losses"))));
            }
            assertThat(revenue).as(groupBy + " revenue").isEqualByComparingTo("1750.00");
            assertThat(expenses).as(groupBy + " expenses").isEqualByComparingTo("425.50");
            assertThat(net).as(groupBy + " net").isEqualByComparingTo("334.50");
        }

        JsonNode byDay = pnl(shop, today.minusDays(2), today, "day");
        assertThat(byDay.path("groups")).hasSize(3);
        assertThat(byDay.path("groups").get(0).path("groupKey").asText()).isEqualTo(today.minusDays(2).toString());
        assertMoney(byDay.path("groups").get(0).path("revenue"), "0");

        Map<String, JsonNode> byOil = new java.util.LinkedHashMap<>();
        for (JsonNode g : pnl(shop, today, today, "oilType").path("groups")) {
            byOil.put(g.path("groupLabel").asText(), g);
        }
        assertThat(byOil).containsKeys("Mustard", "Khali", "General (unallocated)");
        assertMoney(byOil.get("Mustard").path("revenue"), "1550.00");
        assertMoney(byOil.get("Mustard").path("losses"), "50.00");
        assertMoney(byOil.get("Khali").path("revenue"), "200.00");
        assertMoney(byOil.get("General (unallocated)").path("expenses"), "425.50");
        assertMoney(byOil.get("General (unallocated)").path("losses"), "20.00");

        MvcResult csv = mockMvc.perform(get("/api/v1/pnl/export.csv")
                        .param("from", today.toString()).param("to", today.toString()).param("groupBy", "oilType")
                        .header("Authorization", auth(shop.ownerToken())))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")))
                .andReturn();
        assertThat(csv.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .startsWith("Group,Revenue,COGS,Gross Profit,Expenses,Losses,Net Profit")
                .contains("TOTAL,1750.00,920.00,830.00,425.50,70.00,334.50");

        byte[] pdf = mockMvc.perform(get("/api/v1/pnl/report.pdf")
                        .param("from", today.toString()).param("to", today.toString()).param("groupBy", "day")
                        .header("Authorization", auth(shop.ownerToken())))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");

        mockMvc.perform(get("/api/v1/loss-ledger")
                        .param("dateFrom", today.toString()).param("dateTo", today.toString())
                        .header("Authorization", auth(shop.ownerToken())))
                .andExpect(jsonPath("$.data.length()").value(2));
        mockMvc.perform(get("/api/v1/loss-ledger")
                        .param("dateFrom", today.toString()).param("dateTo", today.toString())
                        .param("lossType", "DAMAGE").param("productId", String.valueOf(mustard.getId()))
                        .header("Authorization", auth(shop.ownerToken())))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].productName").value("Mustard 1L"));
    }

    // ── fixtures ──────────────────────────────────────────────────────────────

    private Product product(Shop shop, String sku, String name, String cost, boolean isKhali,
                            com.ghana.dashboard.domain.OilType oilType) {
        Product p = new Product();
        p.setShopId(shop.id());
        p.setOilType(oilType);
        p.setName(name);
        p.setSku(sku);
        p.setPackSize(BigDecimal.ONE);
        p.setUnit(Product.Unit.L);
        p.setContainer(Product.Container.plastic);
        p.setSellingPrice(new BigDecimal("200"));
        p.setMrp(new BigDecimal("220"));
        p.setCostPrice(new BigDecimal(cost));
        p.setLowStockThreshold(BigDecimal.TEN);
        p.setActive(true);
        p.setKhali(isKhali);
        p.setCreatedAt(LocalDateTime.now());
        p.setUpdatedAt(LocalDateTime.now());
        return productRepository.save(p);
    }

    private Sale sale(Shop shop, User user, String subtotal, String total, LocalDateTime at) {
        Sale s = new Sale();
        s.setShopId(shop.id());
        s.setBillNo("B" + ThreadLocalRandom.current().nextLong(1_000_000_000L));
        s.setUser(user);
        s.setSubtotal(new BigDecimal(subtotal));
        s.setDiscount(new BigDecimal(subtotal).subtract(new BigDecimal(total)));
        s.setDiscountType(Sale.DiscountType.AMOUNT);
        s.setTotalAmount(new BigDecimal(total));
        s.setPaidAmount(new BigDecimal(total));
        s.setPaymentMode(Sale.PaymentMode.CASH);
        s.setPaymentStatus(Sale.PaymentStatus.PAID);
        s.setVoid(false);
        s.setActive(true);
        s.setCreatedAt(at);
        s.setUpdatedAt(at);
        return saleRepository.save(s);
    }

    private void saleItem(Sale sale, Product product, String qty, String rate, String amount, String cost) {
        SaleItem i = new SaleItem();
        i.setSale(sale);
        i.setShopId(sale.getShopId());
        i.setProduct(product);
        i.setSku(product.getSku());
        i.setProductName(product.getName());
        i.setQty(new BigDecimal(qty));
        i.setUnit("L");
        i.setRate(new BigDecimal(rate));
        i.setAmount(new BigDecimal(amount));
        i.setCostPrice(new BigDecimal(cost));
        i.setProfit(BigDecimal.ZERO);
        i.setCreatedAt(sale.getCreatedAt());
        saleItemRepository.save(i);
    }

    private Order order(Shop shop, User user, String subtotal, String total, Order.OrderStatus status,
                        LocalDate deliveredOn, LocalDate createdOn) {
        Customer c = new Customer();
        c.setShopId(shop.id());
        c.setName("Cust");
        c.setPhone(String.valueOf(ThreadLocalRandom.current().nextLong(6_000_000_000L, 9_999_999_999L)));
        c.setActive(true);
        c.setCreatedAt(LocalDateTime.now());
        c.setUpdatedAt(LocalDateTime.now());
        customerRepository.save(c);

        CustomerAddress a = new CustomerAddress();
        a.setCustomer(c);
        a.setShopId(shop.id());
        a.setAddressType(CustomerAddress.AddressType.HOME);
        a.setAddressLine1("1 Test Road");
        a.setCity("Accra");
        a.setState("GA");
        a.setPincode("100001");
        a.setActive(true);
        a.setCreatedAt(LocalDateTime.now());
        a.setUpdatedAt(LocalDateTime.now());
        customerAddressRepository.save(a);

        Order o = new Order();
        o.setShopId(shop.id());
        o.setBillNo("O" + ThreadLocalRandom.current().nextLong(1_000_000_000L));
        o.setCustomer(c);
        o.setAddress(a);
        o.setUser(user);
        o.setSubtotal(new BigDecimal(subtotal));
        o.setDeliveryCharge(new BigDecimal(total).subtract(new BigDecimal(subtotal)));
        o.setDiscount(BigDecimal.ZERO);
        o.setDiscountType(Order.DiscountType.AMOUNT);
        o.setTotalAmount(new BigDecimal(total));
        o.setPaidAmount(BigDecimal.ZERO);
        o.setPaymentMode(Order.PaymentMode.COD);
        o.setPaymentStatus(Order.PaymentStatus.UNPAID);
        o.setStatus(status);
        o.setActualDeliveryDate(deliveredOn);
        o.setActive(true);
        o.setCreatedAt(createdOn.atTime(9, 0));
        o.setUpdatedAt(createdOn.atTime(9, 0));
        return orderRepository.save(o);
    }

    private void orderItem(Order order, Product product, String qty, String rate, String amount, String cost) {
        OrderItem i = new OrderItem();
        i.setOrder(order);
        i.setShopId(order.getShopId());
        i.setProduct(product);
        i.setSku(product.getSku());
        i.setProductName(product.getName());
        i.setQty(new BigDecimal(qty));
        i.setUnit("L");
        i.setRate(new BigDecimal(rate));
        i.setAmount(new BigDecimal(amount));
        i.setCostPrice(new BigDecimal(cost));
        i.setProfit(BigDecimal.ZERO);
        i.setCreatedAt(order.getCreatedAt());
        orderItemRepository.save(i);
    }

    private void loss(Shop shop, User user, Product product, String value, LocalDate date) {
        LossLedger l = new LossLedger();
        l.setShopId(shop.id());
        l.setLossType(product != null ? LossLedger.LossType.DAMAGE : LossLedger.LossType.OTHER);
        l.setProductId(product != null ? product.getId() : null);
        l.setQty(BigDecimal.ONE);
        l.setValue(new BigDecimal(value));
        l.setLossDate(date);
        l.setRecordedBy(user.getId());
        lossLedgerRepository.save(l);
    }
}
