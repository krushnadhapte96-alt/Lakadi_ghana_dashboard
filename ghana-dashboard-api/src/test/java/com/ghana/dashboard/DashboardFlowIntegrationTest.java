package com.ghana.dashboard;

import com.ghana.dashboard.domain.Customer;
import com.ghana.dashboard.domain.CustomerAddress;
import com.ghana.dashboard.domain.OilType;
import com.ghana.dashboard.domain.Order;
import com.ghana.dashboard.domain.Product;
import com.ghana.dashboard.domain.ProductBatchStock;
import com.ghana.dashboard.domain.Role;
import com.ghana.dashboard.domain.Sale;
import com.ghana.dashboard.domain.SaleItem;
import com.ghana.dashboard.domain.SeedStock;
import com.ghana.dashboard.domain.SeedType;
import com.ghana.dashboard.domain.User;
import com.ghana.dashboard.repository.CustomerAddressRepository;
import com.ghana.dashboard.repository.CustomerRepository;
import com.ghana.dashboard.repository.OilTypeRepository;
import com.ghana.dashboard.repository.OrderRepository;
import com.ghana.dashboard.repository.ProductBatchStockRepository;
import com.ghana.dashboard.repository.ProductRepository;
import com.ghana.dashboard.repository.SaleItemRepository;
import com.ghana.dashboard.repository.SaleRepository;
import com.ghana.dashboard.repository.SeedStockRepository;
import com.ghana.dashboard.repository.SeedTypeRepository;
import com.ghana.dashboard.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The dashboard is a summary of modules that already exist, so every card has to agree with the
 * screen it links to. These tests read the card and the module in the same transaction and assert
 * the two produce the same number, which is the only way to catch a card silently drifting away
 * from its module.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Rollback
class DashboardFlowIntegrationTest {

    private static final String PASSWORD = "secret1234";
    private static final Long OIL_TYPE_ID = 1L;
    private static final Long SEED_TYPE_ID = 1L;

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private UserRepository userRepository;
    @Autowired private OilTypeRepository oilTypeRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private ProductBatchStockRepository batchStockRepository;
    @Autowired private SaleRepository saleRepository;
    @Autowired private SaleItemRepository saleItemRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private CustomerAddressRepository customerAddressRepository;
    @Autowired private SeedStockRepository seedStockRepository;
    @Autowired private SeedTypeRepository seedTypeRepository;

    private record Shop(Long id, Long ownerId, String ownerToken, String staffToken) {}

    // ── access control and the empty state ────────────────────────────────────

    @Test
    void dashboardNeedsLoginAndStaffNeverSeesTheProfitFigure() throws Exception {
        Shop shop = newShop();

        mockMvc.perform(get("/api/v1/dashboard/summary")).andExpect(status().isUnauthorized());

        sale(shop, owner(shop), "500", Sale.PaymentStatus.PAID, LocalDate.now().atTime(10, 0));
        flush();

        JsonNode owner = dashboard(shop.ownerToken());
        assertMoney(owner.path("monthNetProfit"), "500");
        assertThat(owner.path("monthMarginPercent").asDouble()).isPositive();

        // The staff payload must be the same shape with the owner-only numbers taken out, because
        // the cache key carries the role and a staff entry can never hold a profit figure.
        JsonNode staff = dashboard(shop.staffToken());
        assertThat(staff.path("todaySales").path("amount").asDouble()).isEqualTo(500.0);
        assertThat(staff.path("monthNetProfit").isNull()).isTrue();
        assertThat(staff.path("monthMarginPercent").isNull()).isTrue();

        // No profit card, no P&L to open behind it.
        mockMvc.perform(get("/api/v1/pnl")
                        .param("from", LocalDate.now().withDayOfMonth(1).toString())
                        .param("to", LocalDate.now().toString())
                        .header("Authorization", auth(shop.staffToken())))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/licences/summary").header("Authorization", auth(shop.staffToken())))
                .andExpect(status().isForbidden());
    }

    @Test
    void aBrandNewShopShowsZeroesRatherThanBlanks() throws Exception {
        Shop shop = newShop();

        JsonNode summary = dashboard(shop.ownerToken());

        assertThat(summary.path("date").asText()).isEqualTo(LocalDate.now().toString());
        assertThat(summary.path("shopName").asText()).startsWith("Dash Shop");
        assertMoney(summary.path("todaySales").path("amount"), "0");
        assertThat(summary.path("todaySales").path("bills").asLong()).isZero();
        assertThat(summary.path("pendingOrders").asLong()).isZero();
        assertThat(summary.path("lowStock").path("count").asLong()).isZero();
        assertThat(summary.path("lowStock").path("top")).isEmpty();
        assertThat(summary.path("seedStock").path("totalKg").asDouble()).isZero();
        // Nothing held, but every seed type the shop could buy is still an empty shelf.
        assertThat(summary.path("seedStock").path("seedTypes").asLong()).isPositive();
        assertThat(summary.path("seedStock").path("lowCount").asLong())
                .isEqualTo(summary.path("seedStock").path("seedTypes").asLong());
        assertThat(summary.path("expiringSoon").path("count").asLong()).isZero();
        assertMoney(summary.path("moneyToCollect").path("amount"), "0");
        assertThat(summary.path("licences").path("expiringSoon").asLong()).isZero();
        assertThat(summary.path("licences").path("expired").asLong()).isZero();
        assertThat(summary.path("licences").path("next").isNull()).isTrue();
        assertThat(summary.path("refillDue").path("count").asLong()).isZero();
        assertThat(summary.path("salesSeries")).hasSize(30);
        assertThat(summary.path("salesSeries").get(29).path("date").asText())
                .isEqualTo(LocalDate.now().toString());
        assertThat(summary.path("oilMix")).isEmpty();
        assertThat(summary.path("generatedAt").asText()).isNotBlank();
    }

    // ── each card against the module it summarises ────────────────────────────

    @Test
    void todaySalesMatchesTheSalesModule() throws Exception {
        Shop shop = newShop();
        User user = owner(shop);
        OilType mustard = oilType("Mustard");
        Product oil = product(shop, mustard, "MS-1L", "Mustard Oil 1L", "100", "10");

        sale(shop, user, "500", Sale.PaymentStatus.PAID, LocalDate.now().atTime(9, 30));
        Sale second = sale(shop, user, "300", Sale.PaymentStatus.PAID, LocalDate.now().atTime(18, 45));
        saleItem(second, shop, oil, "3", "100", "300", "80");
        Sale unpaid = sale(shop, user, "200", Sale.PaymentStatus.UNPAID, LocalDate.now().atTime(20, 0));
        unpaid.setPaidAmount(BigDecimal.ZERO);
        saleRepository.saveAndFlush(unpaid);
        // Walk-in bills nobody has paid for are still money to collect.
        saleItem(unpaid, shop, oil, "2", "100", "200", "80");
        flush();

        JsonNode summary = dashboard(shop.ownerToken());

        // Every sale in this shop was billed today, so the unfiltered Sales list is the
        // module's own answer for the same day.
        JsonNode sales = data(api("/api/v1/sales", shop.ownerToken()));
        assertThat(sales.path("totalElements").asLong()).isEqualTo(3);
        BigDecimal moduleTotal = BigDecimal.ZERO;
        for (JsonNode row : sales.path("content")) {
            moduleTotal = moduleTotal.add(dec(row.path("totalAmount")));
        }
        assertMoney(summary.path("todaySales").path("amount"), moduleTotal.toPlainString());
        assertThat(summary.path("todaySales").path("bills").asLong())
                .isEqualTo(sales.path("totalElements").asLong());
        assertMoney(summary.path("todaySales").path("litres"), "5");

        // An unpaid walk-in bill is part of money to collect, and it is not a khata party.
        assertThat(summary.path("moneyToCollect").path("unpaidBills").asLong()).isEqualTo(1);
        assertThat(summary.path("moneyToCollect").path("parties").asLong()).isZero();
    }

    @Test
    void lowStockMatchesTheInventoryModulesOwnFlag() throws Exception {
        Shop shop = newShop();
        OilType mustard = oilType("Mustard");

        Product empty = product(shop, mustard, "LOW-1", "Nearly Out", "100", "12");
        Product healthy = product(shop, mustard, "OK-1", "Plenty", "100", "12");
        Product borderline = product(shop, mustard, "EDGE-1", "Exactly At", "100", "10");
        Product inactive = product(shop, mustard, "OFF-1", "Discontinued", "100", "5");
        inactive.setActive(false);
        productRepository.save(inactive);

        batchStock(shop, healthy, "OK-1", "40", null);
        batchStock(shop, borderline, "EDGE-1", "10", null);
        batchStock(shop, inactive, "OFF-1", "0", null);
        flush();

        JsonNode summary = dashboard(shop.ownerToken());
        JsonNode inventory = data(api("/api/v1/inventory", shop.ownerToken()));

        long flagged = 0;
        for (JsonNode item : concat(inventory.path("oilProducts"), inventory.path("khaliProducts"))) {
            if (item.path("lowStock").asBoolean()) {
                flagged++;
            }
        }
        assertThat(flagged).isEqualTo(1);
        assertThat(summary.path("lowStock").path("count").asLong()).isEqualTo(flagged);
        assertThat(summary.path("lowStock").path("top")).hasSize(1);

        // Stock sitting exactly on the threshold is not low, and a discontinued product is not
        // counted on either screen.
        assertThat(summary.path("lowStock").path("top").get(0).path("productId").asLong())
                .isEqualTo(empty.getId());
        assertThat(summary.path("lowStock").path("top").get(0).path("sku").asText()).isEqualTo("LOW-1");
        assertThat(summary.path("lowStock").path("top").get(0).path("threshold").asDouble()).isEqualTo(12.0);
    }

    @Test
    void pendingOrdersMatchesTheOrdersStatusCounts() throws Exception {
        Shop shop = newShop();
        User user = owner(shop);

        order(shop, user, Order.OrderStatus.NEW);
        order(shop, user, Order.OrderStatus.CONFIRMED);
        order(shop, user, Order.OrderStatus.PACKED);
        order(shop, user, Order.OrderStatus.OUT_FOR_DELIVERY);
        order(shop, user, Order.OrderStatus.DELIVERED);
        order(shop, user, Order.OrderStatus.CANCELLED);
        flush();

        JsonNode summary = dashboard(shop.ownerToken());
        JsonNode counts = data(api("/api/v1/orders/status-counts", shop.ownerToken()));

        long open = counts.path("NEW").asLong() + counts.path("CONFIRMED").asLong()
                + counts.path("PACKED").asLong() + counts.path("OUT_FOR_DELIVERY").asLong();
        assertThat(summary.path("pendingOrders").asLong()).isEqualTo(open);
        assertThat(open).isEqualTo(4);
    }

    @Test
    void expiringSoonMatchesTheExpiryAlertsWindow() throws Exception {
        Shop shop = newShop();
        OilType mustard = oilType("Mustard");
        Product oil = product(shop, mustard, "EXP-1", "Expiring Oil", "100", "5");
        Product khali = product(shop, mustard, "KHA-1", "Expiring Khali", "90", "5");

        LocalDate today = LocalDate.now();
        // Inside the window: already past, inside 7 days, inside 15, inside 30.
        batchStock(shop, oil, "E-EXP", "5", today.minusDays(3));
        batchStock(shop, oil, "E-D7", "5", today.plusDays(4));
        batchStock(shop, khali, "E-D15", "5", today.plusDays(12));
        batchStock(shop, khali, "E-D30", "5", today.plusDays(30));
        // Outside the window, or not a live lot at all.
        batchStock(shop, oil, "E-FAR", "5", today.plusDays(31));
        batchStock(shop, oil, "E-EMPTY", "0", today.plusDays(2));
        batchStock(shop, oil, "E-NODATE", "5", null);
        flush();

        JsonNode summary = dashboard(shop.ownerToken());
        JsonNode alerts = data(api("/api/v1/expiry-alerts", shop.ownerToken()));

        long alertItems = 0;
        BigDecimal unitsAtRisk = BigDecimal.ZERO;
        for (JsonNode bucket : alerts.path("buckets")) {
            alertItems += bucket.path("items").size();
        }
        unitsAtRisk = dec(alerts.path("totalUnitsAtRisk"));

        assertThat(summary.path("expiringSoon").path("count").asLong()).isEqualTo(alertItems);
        assertThat(summary.path("expiringSoon").path("count").asLong()).isEqualTo(4);
        assertThat(summary.path("expiringSoon").path("qtyAtRisk")).isNotNull();
        assertMoney(summary.path("expiringSoon").path("qtyAtRisk"), unitsAtRisk.toPlainString());
        assertThat(summary.path("expiringSoon").path("nearestExpiry").asText())
                .isEqualTo(today.minusDays(3).toString());
        assertThat(summary.path("expiringSoon").path("top")).hasSize(3);
        // The oldest date leads the strip.
        assertThat(summary.path("expiringSoon").path("top").get(0).path("daysLeft").asLong()).isEqualTo(-3);
    }

    @Test
    void seedStockMatchesTheBeejModule() throws Exception {
        Shop shop = newShop();
        seedStock(shop, "500.000");

        JsonNode summary = dashboard(shop.ownerToken());
        JsonNode beej = data(api("/api/v1/seed-stock", shop.ownerToken()));

        BigDecimal moduleKg = BigDecimal.ZERO;
        long lowFlagged = 0;
        for (JsonNode row : beej) {
            moduleKg = moduleKg.add(dec(row.path("availableKg")));
            if (row.path("lowStock").asBoolean()) {
                lowFlagged++;
            }
        }
        assertMoney(summary.path("seedStock").path("totalKg"), moduleKg.toPlainString());
        assertMoney(summary.path("seedStock").path("totalKg"), "500.000");
        // Beej lists every seed type the catalogue knows about and flags the ones the shop holds
        // none of, so the card has to count types and shortages on that same basis or the two
        // screens contradict each other.
        assertThat(summary.path("seedStock").path("seedTypes").asLong()).isEqualTo(beej.size());
        assertThat(summary.path("seedStock").path("lowCount").asLong()).isEqualTo(lowFlagged);
        assertThat(lowFlagged).isPositive();
    }

    @Test
    void refillDueMatchesTheCustomersRefillTab() throws Exception {
        Shop shop = newShop();
        long due = customer(shop).getId();
        long later = customer(shop).getId();
        long never = customer(shop).getId();
        LocalDate today = LocalDate.now();
        jdbcTemplate.update("UPDATE customers SET predicted_refill_date = ? WHERE id = ?",
                today.minusDays(1), due);
        jdbcTemplate.update("UPDATE customers SET predicted_refill_date = ? WHERE id = ?",
                today.plusDays(2), later);
        jdbcTemplate.update("UPDATE customers SET predicted_refill_date = ? WHERE id = ?",
                today.plusDays(40), never);
        flush();

        JsonNode summary = dashboard(shop.ownerToken());
        JsonNode refillTab = data(api("/api/v1/customers/refill-due", shop.ownerToken()));

        assertThat(summary.path("refillDue").path("count").asLong()).isEqualTo(refillTab.size());
        assertThat(summary.path("refillDue").path("count").asLong()).isEqualTo(2);
        assertThat(summary.path("refillDue").path("top")).hasSize(2);
        assertThat(summary.path("refillDue").path("top").get(0).path("daysUntil").asLong()).isEqualTo(-1);
    }

    @Test
    void licencesMatchTheLicencesModuleSummary() throws Exception {
        Shop shop = newShop();
        LocalDate today = LocalDate.now();
        addLicence(shop, "FSSAI", today.plusDays(20));
        addLicence(shop, "GST", today.plusDays(200));
        addLicence(shop, "SHOP_ESTABLISHMENT", today.minusDays(10));
        flush();

        JsonNode summary = dashboard(shop.ownerToken());
        JsonNode licences = data(api("/api/v1/licences/summary", shop.ownerToken()));

        assertThat(summary.path("licences").path("expiringSoon").asLong())
                .isEqualTo(licences.path("expiringSoon").asLong());
        assertThat(summary.path("licences").path("expired").asLong())
                .isEqualTo(licences.path("expired").asLong());
        assertThat(summary.path("licences").path("expiringSoon").asLong()).isEqualTo(1);
        assertThat(summary.path("licences").path("expired").asLong()).isEqualTo(1);

        // The strip leads with whichever licence lapses next, not with the first row inserted.
        assertThat(summary.path("licences").path("next").path("status").asText()).isEqualTo("EXPIRED");
        assertThat(summary.path("licences").path("next").path("daysLeft").asLong()).isEqualTo(-10);
    }

    @Test
    void moneyToCollectSplitsUnpaidBillsFromKhataBalances() throws Exception {
        Shop shop = newShop();
        User user = owner(shop);
        OilType mustard = oilType("Mustard");
        Product oil = product(shop, mustard, "DUE-1", "On Credit", "100", "5");

        Sale walkIn = sale(shop, user, "200", Sale.PaymentStatus.UNPAID, LocalDate.now().atTime(11, 0));
        walkIn.setPaidAmount(BigDecimal.ZERO);
        saleRepository.saveAndFlush(walkIn);
        saleItem(walkIn, shop, oil, "2", "100", "200", "80");
        addKhataParty(shop, "Ravi Store", "500");
        flush();

        JsonNode summary = dashboard(shop.ownerToken());
        JsonNode khata = data(api("/api/v1/khata/summary", shop.ownerToken()));

        assertThat(summary.path("moneyToCollect").path("unpaidBills").asLong()).isEqualTo(1);
        assertThat(summary.path("moneyToCollect").path("parties").asLong()).isEqualTo(1);
        assertThat(summary.path("moneyToCollect").path("parties").asLong())
                .isEqualTo(khata.path("partiesWithBalance").asInt());

        // Khata counts what its own parties owe; the card is that plus the walk-in bills that
        // were never turned into a party, so the card is the wider of the two.
        assertMoney(khata.path("totalOutstanding"), "500");
        assertMoney(summary.path("moneyToCollect").path("amount"), "700");
    }

    @Test
    void monthProfitMatchesTheProfitAndLossModule() throws Exception {
        Shop shop = newShop();
        User user = owner(shop);
        OilType mustard = oilType("Mustard");
        Product oil = product(shop, mustard, "P&L-1", "Priced Oil", "100", "5");

        Sale s = sale(shop, user, "500", Sale.PaymentStatus.PAID, LocalDate.now().atTime(12, 0));
        saleItem(s, shop, oil, "5", "100", "500", "80");
        flush();

        LocalDate today = LocalDate.now();
        JsonNode summary = dashboard(shop.ownerToken());
        JsonNode pnl = data(api("/api/v1/pnl?from=" + today.withDayOfMonth(1) + "&to=" + today
                + "&groupBy=month", shop.ownerToken()));

        assertMoney(summary.path("monthNetProfit"), dec(pnl.path("netProfit")).toPlainString());
        assertMoney(summary.path("monthNetProfit"), "100");
        assertMoney(summary.path("monthMarginPercent"), "20");
    }

    // ── the rolling window behind the chart ───────────────────────────────────

    @Test
    void salesSeriesAndOilMixCoverTheRollingThirtyDaysOnly() throws Exception {
        Shop shop = newShop();
        User user = owner(shop);
        OilType mustard = oilType("Mustard");
        Product oil = product(shop, mustard, "SER-1", "Series Oil", "100", "5");
        Product other = product(shop, oilType("Groundnut"), "SER-2", "Other Oil", "100", "5");

        LocalDate today = LocalDate.now();
        Sale current = sale(shop, user, "400", Sale.PaymentStatus.PAID, today.atTime(10, 0));
        saleItem(current, shop, oil, "4", "100", "400", "80");
        Sale stale = sale(shop, user, "900", Sale.PaymentStatus.PAID, today.minusDays(45).atTime(10, 0));
        saleItem(stale, shop, other, "9", "100", "900", "80");
        flush();

        JsonNode summary = dashboard(shop.ownerToken());

        assertThat(summary.path("salesSeries")).hasSize(30);
        BigDecimal seriesTotal = BigDecimal.ZERO;
        long seriesBills = 0;
        for (JsonNode point : summary.path("salesSeries")) {
            seriesTotal = seriesTotal.add(dec(point.path("amount")));
            seriesBills += point.path("bills").asLong();
        }
        // The 45-day-old bill has rolled off the chart even though it is still in the month.
        assertMoney(summary.path("todaySales").path("amount"), "400");
        assertMoney(seriesTotal, "400");
        assertThat(seriesBills).isEqualTo(1);

        BigDecimal mix = BigDecimal.ZERO;
        for (JsonNode slice : summary.path("oilMix")) {
            mix = mix.add(dec(slice.path("amount")));
            assertThat(slice.path("colourTag").asText()).isNotBlank();
        }
        assertMoney(mix, "400");
    }

    // ── caching, eviction and tenancy ─────────────────────────────────────────

    @Test
    void aSecondReadIsServedFromCacheAndAMutationDropsIt() throws Exception {
        Shop shop = newShop();
        seedStock(shop, "500.000");

        JsonNode first = dashboard(shop.ownerToken());
        assertMoney(first.path("seedStock").path("totalKg"), "500.000");

        // Same shop, same role: cached, so nothing recomputed and the payload is identical.
        JsonNode cached = dashboard(shop.ownerToken());
        assertThat(cached.path("generatedAt").asText()).isEqualTo(first.path("generatedAt").asText());
        assertThat(cached.path("seedStock").path("totalKg").asDouble()).isEqualTo(500.0);

        // Pressing a batch eats seed, which changes a card, so the entry has to be dropped.
        pressBatch(shop, "50.000");
        flush();
        JsonNode afterMutation = dashboard(shop.ownerToken());
        assertMoney(afterMutation.path("seedStock").path("totalKg"), "450.000");
    }

    @Test
    void oneShopNeverSeesAnotherShopsNumbers() throws Exception {
        Shop mine = newShop();
        Shop theirs = newShop();
        User theirUser = owner(theirs);
        OilType mustard = oilType("Mustard");
        Product theirOil = product(theirs, mustard, "OTHER-1", "Their Oil", "100", "5");

        Sale theirSale = sale(theirs, theirUser, "7500", Sale.PaymentStatus.PAID,
                LocalDate.now().atTime(14, 0));
        saleItem(theirSale, theirs, theirOil, "75", "100", "7500", "80");
        seedStock(theirs, "900.000");
        flush();

        JsonNode mineSummary = dashboard(mine.ownerToken());
        assertMoney(mineSummary.path("todaySales").path("amount"), "0");
        assertMoney(mineSummary.path("seedStock").path("totalKg"), "0");
        assertThat(mineSummary.path("lowStock").path("count").asLong()).isZero();
        assertMoney(mineSummary.path("moneyToCollect").path("amount"), "0");
        assertMoney(mineSummary.path("monthNetProfit"), "0");
        assertThat(mineSummary.path("salesSeries")).allSatisfy(point ->
                assertMoney(point.path("amount"), "0"));
    }

    // ── fixtures ──────────────────────────────────────────────────────────────

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
                                "shopName", "Dash Shop " + phone, "ownerName", "Owner " + phone,
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

    private JsonNode dashboard(String token) throws Exception {
        return data(api("/api/v1/dashboard/summary", token));
    }

    private MvcResult api(String url, String token) throws Exception {
        return mockMvc.perform(get(url).header("Authorization", auth(token)))
                .andExpect(status().isOk()).andReturn();
    }

    private void pressBatch(Shop shop, String seedKg) throws Exception {
        seedStock(shop, "500.000");
        mockMvc.perform(post("/api/v1/batches")
                        .header("Authorization", auth(shop.ownerToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "oilTypeId", OIL_TYPE_ID,
                                "seedTypeId", SEED_TYPE_ID,
                                "seedKg", seedKg,
                                "pressStart", "2026-01-05T09:00:00"))))
                .andExpect(status().isOk());
    }

    private void seedStock(Shop shop, String kg) {
        SeedStock stock = seedStockRepository.findByShopIdAndSeedTypeId(shop.id(), SEED_TYPE_ID)
                .orElseGet(() -> {
                    SeedStock fresh = new SeedStock();
                    fresh.setShopId(shop.id());
                    fresh.setSeedType(seedTypeRepository.findById(SEED_TYPE_ID).orElseThrow());
                    fresh.setAvgRate(new BigDecimal("120.00"));
                    fresh.setLowStockThreshold(new BigDecimal("100.000"));
                    return fresh;
                });
        stock.setAvailableKg(new BigDecimal(kg));
        seedStockRepository.save(stock);
    }

    private void addLicence(Shop shop, String type, LocalDate expiry) throws Exception {
        mockMvc.perform(multipart("/api/v1/licences")
                        .file(new MockMultipartFile("licence", "", MediaType.APPLICATION_JSON_VALUE,
                                objectMapper.writeValueAsBytes(Map.of(
                                        "licenceNumber", type + "-" + ThreadLocalRandom.current().nextLong(100000L, 999999L),
                                        "type", type,
                                        "issuer", "Test Authority",
                                        "issueDate", LocalDate.now().minusYears(1).toString(),
                                        "expiryDate", expiry.toString()))))
                        .header("Authorization", auth(shop.ownerToken()))
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isCreated());
    }

    private void addKhataParty(Shop shop, String name, String openingBalance) throws Exception {
        mockMvc.perform(post("/api/v1/khata/parties")
                        .header("Authorization", auth(shop.ownerToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", name, "type", "RETAILER", "phone",
                                String.valueOf(ThreadLocalRandom.current().nextLong(6_000_000_000L, 9_999_999_999L)),
                                "openingBalance", openingBalance))))
                .andExpect(status().isCreated());
    }

    private User owner(Shop shop) {
        return userRepository.findById(shop.ownerId()).orElseThrow();
    }

    private OilType oilType(String name) {
        return oilTypeRepository.findByNameEn(name).orElseThrow();
    }

    private Product product(Shop shop, OilType oilType, String sku, String name, String cost,
                            String lowStockThreshold) {
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
        p.setLowStockThreshold(lowStockThreshold == null ? null : new BigDecimal(lowStockThreshold));
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
        stock.setBatchId(batchId + "-" + ThreadLocalRandom.current().nextLong(1000L, 9999L));
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

    private Sale sale(Shop shop, User user, String total, Sale.PaymentStatus paymentStatus,
                      LocalDateTime at) {
        Sale s = new Sale();
        s.setShopId(shop.id());
        s.setBillNo("D" + ThreadLocalRandom.current().nextLong(1_000_000_000L));
        s.setUser(user);
        s.setSubtotal(new BigDecimal(total));
        s.setDiscount(BigDecimal.ZERO);
        s.setDiscountType(Sale.DiscountType.AMOUNT);
        s.setTotalAmount(new BigDecimal(total));
        s.setPaidAmount(new BigDecimal(total));
        s.setPaymentMode(Sale.PaymentMode.CASH);
        s.setPaymentStatus(paymentStatus);
        s.setVoid(false);
        s.setActive(true);
        s.setCreatedAt(at);
        s.setUpdatedAt(at);
        return saleRepository.save(s);
    }

    private void saleItem(Sale sale, Shop shop, Product product, String qty, String rate, String amount,
                          String cost) {
        SaleItem i = new SaleItem();
        i.setSale(sale);
        i.setShopId(shop.id());
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

    private Order order(Shop shop, User user, Order.OrderStatus status) {
        Customer c = new Customer();
        c.setShopId(shop.id());
        c.setName("Order Customer");
        c.setPhone(String.valueOf(ThreadLocalRandom.current().nextLong(6_000_000_000L, 9_999_999_999L)));
        c.setActive(true);
        c.setCreatedAt(LocalDateTime.now());
        c.setUpdatedAt(LocalDateTime.now());
        customerRepository.save(c);

        CustomerAddress a = new CustomerAddress();
        a.setCustomer(c);
        a.setShopId(shop.id());
        a.setAddressType(CustomerAddress.AddressType.HOME);
        a.setAddressLine1("1 Dashboard Road");
        a.setCity("Accra");
        a.setState("GA");
        a.setPincode("100001");
        a.setActive(true);
        a.setCreatedAt(LocalDateTime.now());
        a.setUpdatedAt(LocalDateTime.now());
        customerAddressRepository.save(a);

        Order o = new Order();
        o.setShopId(shop.id());
        o.setBillNo("OD" + ThreadLocalRandom.current().nextLong(1_000_000_000L));
        o.setCustomer(c);
        o.setAddress(a);
        o.setUser(user);
        o.setSubtotal(new BigDecimal("500"));
        o.setDeliveryCharge(BigDecimal.ZERO);
        o.setDiscount(BigDecimal.ZERO);
        o.setDiscountType(Order.DiscountType.AMOUNT);
        o.setTotalAmount(new BigDecimal("500"));
        o.setPaidAmount(BigDecimal.ZERO);
        o.setPaymentMode(Order.PaymentMode.COD);
        o.setPaymentStatus(Order.PaymentStatus.UNPAID);
        o.setStatus(status);
        o.setActive(true);
        o.setCreatedAt(LocalDateTime.now());
        o.setUpdatedAt(LocalDateTime.now());
        return orderRepository.save(o);
    }

    private Customer customer(Shop shop) {
        Customer c = new Customer();
        c.setShopId(shop.id());
        c.setName("Refill Customer");
        c.setPhone(String.valueOf(ThreadLocalRandom.current().nextLong(6_000_000_000L, 9_999_999_999L)));
        c.setActive(true);
        c.setCreatedAt(LocalDateTime.now());
        c.setUpdatedAt(LocalDateTime.now());
        return customerRepository.save(c);
    }

    /** The dashboard reads through plain SQL, so the writes have to reach the table first. */
    private void flush() {
        entityManager.flush();
        entityManager.clear();
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

    private static BigDecimal dec(JsonNode node) {
        return new BigDecimal(node.asText());
    }

    private static void assertMoney(JsonNode actual, String expected) {
        assertThat(dec(actual)).isEqualByComparingTo(expected);
    }

    private static void assertMoney(BigDecimal actual, String expected) {
        assertThat(actual).isEqualByComparingTo(expected);
    }

    private static List<JsonNode> concat(JsonNode a, JsonNode b) {
        List<JsonNode> all = new ArrayList<>();
        a.forEach(all::add);
        b.forEach(all::add);
        return all;
    }
}