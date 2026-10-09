package com.ghana.dashboard;

import com.ghana.dashboard.domain.Expense;
import com.ghana.dashboard.domain.Product;
import com.ghana.dashboard.domain.ProductBatchStock;
import com.ghana.dashboard.domain.Role;
import com.ghana.dashboard.domain.Sale;
import com.ghana.dashboard.domain.SaleItem;
import com.ghana.dashboard.domain.SeedStock;
import com.ghana.dashboard.domain.SeedType;
import com.ghana.dashboard.domain.Shop;
import com.ghana.dashboard.domain.User;
import com.ghana.dashboard.repository.ExpenseRepository;
import com.ghana.dashboard.repository.OrderRepository;
import com.ghana.dashboard.repository.ProductBatchStockRepository;
import com.ghana.dashboard.repository.ProductRepository;
import com.ghana.dashboard.repository.SaleItemRepository;
import com.ghana.dashboard.repository.SaleRepository;
import com.ghana.dashboard.repository.SeedStockRepository;
import com.ghana.dashboard.repository.SeedTypeRepository;
import com.ghana.dashboard.repository.ShopRepository;
import com.ghana.dashboard.repository.UserRepository;
import com.ghana.dashboard.service.CustomerInsightService;
import com.ghana.dashboard.service.InventoryService;
import com.ghana.dashboard.service.KhataLedgerService;
import com.ghana.dashboard.service.ProfitLossService;
import com.ghana.dashboard.service.SeedStockService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Direct service-level tests: the arithmetic that decides stock, spend and the P&L.
 *
 * <p>These run against real MySQL like the rest of the suite, wrapped in a rollback so nothing
 * escapes a test. A misplaced digit in a cost or balance is invisible from the outside - the UI
 * would show a confidently wrong number - so each service gets a dedicated assertion.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Rollback
class ServiceLogicIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private EntityManager entityManager;
    @Autowired private InventoryService inventoryService;
    @Autowired private SeedStockService seedStockService;
    @Autowired private ProfitLossService profitLossService;
    @Autowired private KhataLedgerService khataLedger;
    @Autowired private SeedStockRepository seedStockRepository;
    @Autowired private SeedTypeRepository seedTypeRepository;
    @Autowired private ShopRepository shopRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ExpenseRepository expenseRepository;
    @Autowired private SaleRepository saleRepository;
    @Autowired private SaleItemRepository saleItemRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private ProductBatchStockRepository batchStockRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private Shop shop() {
        Shop s = new Shop();
        s.setName("Logic Shop " + ThreadLocalRandom.current().nextInt(10000, 99999));
        s.setMobile(String.valueOf(ThreadLocalRandom.current().nextLong(6_000_000_000L, 9_999_999_999L)));
        s.setCurrencyCode("INR");
        s.setStatus("ACTIVE");
        s.setCreatedAt(LocalDateTime.now());
        s.setUpdatedAt(LocalDateTime.now());
        return shopRepository.save(s);
    }

    private Product product(Shop shop) {
        Product p = new Product();
        p.setShopId(shop.getId());
        p.setName("Logic Milk");
        p.setSku("LOGIC-" + ThreadLocalRandom.current().nextInt(1000, 9999));
        p.setPackSize(BigDecimal.ONE);
        p.setUnit(Product.Unit.L);
        p.setContainer(Product.Container.plastic);
        p.setSellingPrice(new BigDecimal("60"));
        p.setMrp(new BigDecimal("65"));
        p.setCostPrice(new BigDecimal("50"));
        p.setLowStockThreshold(new BigDecimal("3"));
        p.setActive(true);
        p.setKhali(false);
        p.setCreatedAt(LocalDateTime.now());
        p.setUpdatedAt(LocalDateTime.now());
        return productRepository.save(p);
    }

    private void stock(Shop shop, Product p, String batchId, String qty, int receivedDaysAgo) {
        ProductBatchStock s = new ProductBatchStock();
        s.setShopId(shop.getId());
        s.setProduct(p);
        s.setBatchId(batchId);
        s.setQtyAvailable(new BigDecimal(qty));
        s.setQtyReserved(BigDecimal.ZERO);
        s.setCostPrice(p.getCostPrice());
        s.setReceivedDate(LocalDate.now().minusDays(receivedDaysAgo));
        s.setExpiryDate(LocalDate.now().plusMonths(6));
        s.setActive(true);
        s.setCreatedAt(LocalDateTime.now());
        s.setUpdatedAt(LocalDateTime.now());
        batchStockRepository.save(s);
    }

    private void flush() {
        entityManager.flush();
        entityManager.clear();
    }

    // ── InventoryService ───────────────────────────────────────────────────

    @Test
    void fifoDeductionEmptiesOldestBatchFirst() {
        Shop shop = shop();
        User u = addUser(shop, "Counter", "9800000001", Role.STAFF);
        Product p = product(shop);
        stock(shop, p, "OLD", "10", 10);
        stock(shop, p, "NEW", "10", 5);
        flush();

        inventoryService.deductStockFIFO(p, new BigDecimal("12"), u.getId(), LocalDateTime.now(),
                "Sale: INV-1", "SALE", 1L);
        flush();

        List<ProductBatchStock> batches = inventoryService.getBatches(shop.getId(), p.getId());
        BigDecimal oldTotal = batches.stream().filter(b -> "OLD".equals(b.getBatchId()))
                .map(ProductBatchStock::getQtyAvailable).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal newTotal = batches.stream().filter(b -> "NEW".equals(b.getBatchId()))
                .map(ProductBatchStock::getQtyAvailable).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(oldTotal).isEqualByComparingTo("0");
        assertThat(newTotal).isEqualByComparingTo("8");
        assertThat(inventoryService.getAvailableStock(shop.getId(), p.getId())).isEqualByComparingTo("8");
    }

    @Test
    void reserveAndReleaseMovesStockBetweenPools() {
        Shop shop = shop();
        User u = addUser(shop, "Counter", "9800000002", Role.STAFF);
        Product p = product(shop);
        stock(shop, p, "ONLY", "10", 5);
        flush();

        inventoryService.reserveStock(p, new BigDecimal("4"), u.getId(), LocalDateTime.now(), "SALE", 1L);
        flush();
        assertThat(inventoryService.getReservedStock(shop.getId(), p.getId())).isEqualByComparingTo("4");
        // getAvailableStock folds reserved back in; the reservable pool is the excluding one.
        assertThat(inventoryService.getAvailableStockExcludingReserved(shop.getId(), p.getId()))
                .isEqualByComparingTo("6");
        assertThat(inventoryService.getAvailableStock(shop.getId(), p.getId())).isEqualByComparingTo("10");

        assertThatThrownBy(() -> inventoryService.deductStockFIFO(p, new BigDecimal("7"), u.getId(),
                        LocalDateTime.now(), "Sale: X", "SALE", 2L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Insufficient");

        inventoryService.releaseReservation(p, new BigDecimal("4"), u.getId(), LocalDateTime.now(), "SALE", 1L);
        flush();
        assertThat(inventoryService.getReservedStock(shop.getId(), p.getId())).isEqualByComparingTo("0");
        assertThat(inventoryService.getAvailableStockExcludingReserved(shop.getId(), p.getId()))
                .isEqualByComparingTo("10");
    }

    @Test
    void deductingMoreThanAvailableIsRefused() {
        Shop shop = shop();
        User u = addUser(shop, "Counter", "9800000003", Role.STAFF);
        Product p = product(shop);
        stock(shop, p, "ONLY", "3", 5);
        flush();

        assertThatThrownBy(() -> inventoryService.deductStockFIFO(p, new BigDecimal("4"), u.getId(),
                        LocalDateTime.now(), "Sale: X", "SALE", 1L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Insufficient");
        flush();
        assertThat(inventoryService.getAvailableStock(shop.getId(), p.getId())).isEqualByComparingTo("3");
    }

    // ── SeedStockService ───────────────────────────────────────────────────

    @Test
    void seedStockConsumeAndAdjustAreArithmeticExact() {
        Shop shop = shop();
        User u = addUser(shop, "Counter", "9800000004", Role.STAFF);
        SeedType st = seedTypeRepository.findAll().get(0);
        SeedStock ss = new SeedStock();
        ss.setShopId(shop.getId());
        ss.setSeedType(st);
        ss.setAvailableKg(new BigDecimal("1000"));
        ss.setAvgRate(new BigDecimal("95"));
        ss.setLowStockThreshold(new BigDecimal("100"));
        ss.setUpdatedAt(LocalDateTime.now());
        seedStockRepository.save(ss);
        flush();

        seedStockService.consume(shop.getId(), st.getId(), new BigDecimal("250"), "Pressed", u.getId(), "BATCH", 1L);
        flush();
        assertThat(seedStockService.getAvailable(shop.getId(), st.getId())).isEqualByComparingTo("750");

        assertThatThrownBy(() -> seedStockService.consume(shop.getId(), st.getId(),
                        new BigDecimal("800"), "Too big", u.getId(), "BATCH", 2L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Insufficient");
        flush();
        assertThat(seedStockService.getAvailable(shop.getId(), st.getId())).isEqualByComparingTo("750");
    }

    // ── KhataLedgerService.standing ────────────────────────────────────────

    @Test
    void khataBalanceNetAndOverdueAreFifoApplied() {
        KhataLedgerService.PartyRow party = new KhataLedgerService.PartyRow(
                1L, 1L, null, "Seed", "INDIVIDUAL", "9000000000", null,
                new BigDecimal("30000"), 30, BigDecimal.ZERO, true, LocalDateTime.now());
        List<KhataLedgerService.EntryRow> entries = List.of(
                new KhataLedgerService.EntryRow(1L, "CREDIT_SALE", new BigDecimal("10000"),
                        new BigDecimal("10000"), LocalDate.now().minusDays(20), LocalDate.now().minusDays(5)),
                new KhataLedgerService.EntryRow(2L, "PAYMENT_RECEIVED", new BigDecimal("4000"),
                        new BigDecimal("-4000"), LocalDate.now().minusDays(3), null));

        KhataLedgerService.Standing standing = KhataLedgerService.standing(party, entries, LocalDate.now());
        assertThat(standing.balance()).isEqualByComparingTo("6000");
        assertThat(standing.overdue()).isEqualByComparingTo("6000");
        assertThat(standing.overdueDays()).isEqualTo(5);
    }

    // ── Refill prediction ──────────────────────────────────────────────────

    @Test
    void refillPredictionAveragesGapsBetweenPurchases() {
        CustomerInsightService.Prediction p = CustomerInsightService.predict(List.of(
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 8),
                LocalDate.of(2026, 9, 15), LocalDate.of(2026, 9, 22)));
        assertThat(p.avgGapDays()).isEqualByComparingTo("7.0");
        assertThat(p.predictedDate()).isEqualTo(LocalDate.of(2026, 9, 29));
    }

    @Test
    void refillPredictionNeedsAtLeastTwoPurchases() {
        CustomerInsightService.Prediction p = CustomerInsightService.predict(List.of(LocalDate.of(2026, 1, 1)));
        assertThat(p.avgGapDays()).isNull();
        assertThat(p.predictedDate()).isNull();
    }

    // ── P&L ────────────────────────────────────────────────────────────────

    @Test
    void profitAndLossMatchesSalesMinusCostsMinusExpenses() {
        Shop shop = shop();
        Product p = product(shop);
        stock(shop, p, "ONLY", "50", 5);
        User user = new User();
        user.setShopId(shop.getId());
        user.setName("Pnl");
        user.setPhone(String.valueOf(ThreadLocalRandom.current().nextLong(6_000_000_000L, 9_999_999_999L)));
        user.setPassword(passwordEncoder.encode("password"));
        user.setRole(Role.OWNER);
        user.setActive(true);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
        flush();

        Sale sale = new Sale();
        sale.setShopId(shop.getId());
        sale.setBillNo("PNL-1");
        sale.setUser(user);
        sale.setSubtotal(new BigDecimal("600"));
        sale.setDiscount(BigDecimal.ZERO);
        sale.setDiscountType(Sale.DiscountType.AMOUNT);
        sale.setTotalAmount(new BigDecimal("600"));
        sale.setPaidAmount(new BigDecimal("600"));
        sale.setPaymentMode(Sale.PaymentMode.CASH);
        sale.setPaymentStatus(Sale.PaymentStatus.PAID);
        sale.setVoid(false);
        sale.setActive(true);
        sale.setCreatedAt(LocalDate.now().atTime(12, 0));
        sale.setUpdatedAt(LocalDate.now().atTime(12, 0));
        saleRepository.save(sale);

        SaleItem item = new SaleItem();
        item.setSale(sale);
        item.setShopId(shop.getId());
        item.setProduct(p);
        item.setSku(p.getSku());
        item.setProductName(p.getName());
        item.setQty(new BigDecimal("10"));
        item.setUnit("L");
        item.setRate(new BigDecimal("60"));
        item.setAmount(new BigDecimal("600"));
        item.setCostPrice(new BigDecimal("50"));
        item.setProfit(new BigDecimal("100"));
        item.setCreatedAt(LocalDate.now().atTime(12, 0));
        saleItemRepository.save(item);

        Expense e = new Expense();
        e.setShopId(shop.getId());
        e.setCategoryId(1L);
        e.setAmount(new BigDecimal("10"));
        e.setTitle("test expense");
        e.setExpenseDate(LocalDate.now());
        e.setCreatedBy(user.getId());
        e.setCreatedAt(LocalDateTime.now());
        e.setUpdatedAt(LocalDateTime.now());
        expenseRepository.save(e);
        flush();

        var pnl = profitLossService.getProfitLoss(shop.getId(), LocalDate.now().minusDays(1),
                LocalDate.now().plusDays(1), "month");
        assertThat(pnl.totalRevenue()).isEqualByComparingTo("600");
        assertThat(pnl.totalCogs()).isEqualByComparingTo("500");
        assertThat(pnl.totalExpenses()).isEqualByComparingTo("10");
        assertThat(pnl.netProfit()).isEqualByComparingTo("90");
    }

    // ── Shop isolation ─────────────────────────────────────────────────────

    @Test
    void oneShopsStandingNeverAppearsInAnothersPartyList() {
        Shop b = shop();
        assertThat(khataLedger.listParties(b.getId()).stream()
                .filter(p -> p.name() != null && p.name().startsWith("Anwar")).toList()).isEmpty();
    }

    // ── STAFF restrictions ─────────────────────────────────────────────────

    @Test
    void staffCannotSeeProfitAndLoss() throws Exception {
        Shop shop = shop();
        String phone = String.valueOf(
                ThreadLocalRandom.current().nextLong(6_000_000_000L, 9_999_999_999L));
        String staffPhone = String.valueOf(
                ThreadLocalRandom.current().nextLong(6_000_000_000L, 9_999_999_999L));
        addUser(shop, "Owner", phone, Role.OWNER);
        addUser(shop, "Staff", staffPhone, Role.STAFF);
        flush();

        var ownerResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("identifier", phone, "password", "password"))))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andReturn();
        var staffResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("identifier", staffPhone, "password", "password"))))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andReturn();
        String ownerToken = objectMapper.readTree(ownerResult.getResponse().getContentAsString())
                .path("data").path("accessToken").asText();
        String staffToken = objectMapper.readTree(staffResult.getResponse().getContentAsString())
                .path("data").path("accessToken").asText();

        mockMvc.perform(get("/api/v1/pnl")
                        .param("from", LocalDate.now().minusDays(7).toString())
                        .param("to", LocalDate.now().toString())
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        mockMvc.perform(get("/api/v1/pnl")
                        .param("from", LocalDate.now().minusDays(7).toString())
                        .param("to", LocalDate.now().toString())
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
    }

    private User addUser(Shop shop, String name, String phone, Role role) {
        User u = new User();
        u.setShopId(shop.getId());
        u.setName(name);
        u.setPhone(phone);
        u.setPassword(passwordEncoder.encode("password"));
        u.setRole(role);
        u.setActive(true);
        u.setCreatedAt(LocalDateTime.now());
        u.setUpdatedAt(LocalDateTime.now());
        return userRepository.save(u);
    }

    // ── Repository queries (smoke) ─────────────────────────────────────────

    @Test
    void seedStockRepositoryFindsByShopAndSeedType() {
        Shop shop = shop();
        SeedType st = seedTypeRepository.findAll().get(0);
        SeedStock ss = new SeedStock();
        ss.setShopId(shop.getId());
        ss.setSeedType(st);
        ss.setAvailableKg(new BigDecimal("123"));
        ss.setAvgRate(new BigDecimal("95"));
        ss.setLowStockThreshold(new BigDecimal("100"));
        ss.setUpdatedAt(LocalDateTime.now());
        seedStockRepository.save(ss);
        flush();

        var found = seedStockRepository.findByShopIdAndSeedTypeId(shop.getId(), st.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getAvailableKg()).isEqualByComparingTo("123");
    }

    @Test
    void orderRepositoryReturnsNewestFirst() {
        Shop shop = shop();
        assertThat(orderRepository.findByShopIdAndActiveTrueOrderByCreatedAtDesc(shop.getId())).isEmpty();
    }
}