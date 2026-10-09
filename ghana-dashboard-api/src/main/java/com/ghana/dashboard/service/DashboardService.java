package com.ghana.dashboard.service;

import com.ghana.dashboard.common.DateDefaults;
import com.ghana.dashboard.common.JdbcSupport;
import com.ghana.dashboard.config.CacheConfig;
import com.ghana.dashboard.domain.Role;
import com.ghana.dashboard.dto.DashboardDtos;
import com.ghana.dashboard.dto.ExpenseDtos;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The home dashboard: one call that answers everything the landing screen shows.
 *
 * <p>Every figure is defined to match the module its card links to, so the number on the card and
 * the number in the module agree:
 * <ul>
 *   <li><b>todaySales</b> — walk-in bills booked today (voided/soft-deleted rows excluded). This is
 *       the Sales module filtered to today; orders are counted separately under pendingOrders and
 *       only land in revenue once delivered, which is why they are not added here.</li>
 *   <li><b>lowStock</b> — products whose available stock (available + reserved, across every batch,
 *       including inactive ones) is under their own threshold, which is exactly how
 *       {@code ProductService} flags the Inventory list.</li>
 *   <li><b>seedStock</b> — kilograms held in {@code seed_stock}, i.e. the Beej module's stock cards.</li>
 *   <li><b>expiringSoon</b> — live lots (expiry date, else best-before) inside the 30-day Expiry
 *       Alerts window, snoozed lots excluded, so the card never contradicts that screen.</li>
 *   <li><b>monthNetProfit</b> — straight from {@link ProfitLossService}, so the P&amp;L module and the
 *       card cannot drift. OWNER only; never computed for a staff account.</li>
 *   <li><b>moneyToCollect</b> — udhaar owed: unpaid walk-in bills that never reached the khata ledger,
 *       plus each active party's opening balance and entry deltas. A credit sale appears in exactly
 *       one of the two legs, so nothing is counted twice.</li>
 * </ul>
 *
 * <p>The whole summary is cached per shop for 60s ({@link com.ghana.dashboard.config.CacheConfig}) and
 * evicted by {@link DashboardCache} the moment a sale, order or batch changes the numbers.
 */
@Service
public class DashboardService {

    private static final int SERIES_DAYS = 30;
    private static final int EXPIRY_WINDOW_DAYS = 30;
    /** Mirrors {@code CustomerInsightService.REFILL_LEAD_DAYS} so the card matches the Customers tab. */
    private static final int REFILL_LEAD_DAYS = 3;
    private static final int TOP_N = 3;

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    private final JdbcSupport db;
    private final ProfitLossService profitLossService;
    private final ShopService shopService;

    public DashboardService(JdbcSupport db, ProfitLossService profitLossService, ShopService shopService) {
        this.db = db;
        this.profitLossService = profitLossService;
        this.shopService = shopService;
    }

    /**
     * Cached per shop <em>and</em> per role: a staff key never holds a profit figure, so the two
     * roles cannot see each other's entry.
     */
    @Cacheable(cacheNames = CacheConfig.SUMMARY_CACHE, key = "#shopId + ':' + #role")
    @Transactional(readOnly = true)
    public DashboardDtos.Summary summary(Long shopId, Role role) {
        LocalDate today = DateDefaults.today();
        LocalDate seriesStart = today.minusDays(SERIES_DAYS - 1);
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("shop", shopId)
                .addValue("today", Date.valueOf(today))
                .addValue("soon", Date.valueOf(today.plusDays(EXPIRY_WINDOW_DAYS)))
                .addValue("dayStart", Timestamp.valueOf(today.atStartOfDay()))
                .addValue("dayEnd", Timestamp.valueOf(today.plusDays(1).atStartOfDay()))
                .addValue("seriesStart", Timestamp.valueOf(seriesStart.atStartOfDay()))
                .addValue("seriesEnd", Timestamp.valueOf(today.plusDays(1).atStartOfDay()))
                .addValue("refillDue", Date.valueOf(today.plusDays(REFILL_LEAD_DAYS)));

        DashboardDtos.TodaySales todaySales = todaySales(p);
        List<DashboardDtos.SalesPoint> series = salesSeries(p, today, seriesStart);
        // Computed at most once, and never for STAFF: the owner-only P&L is the expensive part.
        Profit profit = role == Role.OWNER ? monthNetProfit(shopId, today) : null;

        return new DashboardDtos.Summary(
                today,
                shopService.findById(shopId).getName(),
                todaySales,
                pendingOrders(p),
                lowStock(p),
                seedStock(p),
                expiringSoon(p, today),
                profit == null ? null : profit.net(),
                profit == null ? null : profit.margin(),
                moneyToCollect(p),
                licenceAlerts(p, today),
                refillDue(p, today),
                series,
                oilMix(p),
                LocalDateTime.now());
    }

    // ==================== cards ====================

    private DashboardDtos.TodaySales todaySales(MapSqlParameterSource p) {
        Map<String, Object> row = db.jdbc().queryForMap("""
                SELECT COUNT(DISTINCT s.id) AS bills,
                       COALESCE(SUM(s.total_amount), 0) AS amount,
                       COALESCE(SUM(CASE WHEN si.unit = 'L' THEN si.qty
                                         WHEN si.unit = 'ML' THEN si.qty / 1000
                                         ELSE 0 END), 0) AS litres
                FROM sales s LEFT JOIN sale_items si ON si.sale_id = s.id
                WHERE s.shop_id = :shop AND s.is_void = 0 AND s.active = 1
                  AND s.created_at >= :dayStart AND s.created_at < :dayEnd""", p);
        return new DashboardDtos.TodaySales(
                money((BigDecimal) row.get("amount")),
                qty((BigDecimal) row.get("litres")),
                ((Number) row.get("bills")).longValue());
    }

    private long pendingOrders(MapSqlParameterSource p) {
        Long count = db.jdbc().queryForObject("""
                SELECT COUNT(*) FROM orders
                WHERE shop_id = :shop AND active = 1 AND status NOT IN ('DELIVERED', 'CANCELLED')""",
                p, Long.class);
        return count == null ? 0 : count;
    }

    private DashboardDtos.LowStock lowStock(MapSqlParameterSource p) {
        List<DashboardDtos.LowStockItem> all = db.jdbc().query("""
                SELECT pr.id, pr.name, pr.sku,
                       COALESCE(SUM(b.qty_available + b.qty_reserved), 0) AS available,
                       pr.low_stock_threshold AS threshold
                FROM products pr LEFT JOIN product_batch_stock b ON b.product_id = pr.id
                WHERE pr.shop_id = :shop AND pr.active = 1
                GROUP BY pr.id, pr.name, pr.sku, pr.low_stock_threshold
                HAVING available < threshold
                ORDER BY available, pr.name""", p, (rs, i) -> new DashboardDtos.LowStockItem(
                        rs.getLong("id"), rs.getString("name"), rs.getString("sku"),
                        qty(rs.getBigDecimal("available")), qty(rs.getBigDecimal("threshold"))));
        return new DashboardDtos.LowStock(all.size(), all.subList(0, Math.min(TOP_N, all.size())));
    }

    private DashboardDtos.SeedStock seedStock(MapSqlParameterSource p) {
        // The Beej screen lists every seed type in the catalogue and treats a type the shop has
        // never bought as zero kg against the default threshold, so the card counts them the same
        // way: otherwise a shop that holds nothing would read "0 low" above a screen full of red.
        Map<String, Object> row = db.jdbc().queryForMap("""
                SELECT COALESCE(SUM(COALESCE(ss.available_kg, 0)), 0) AS total_kg,
                       COUNT(*) AS seed_types,
                       COALESCE(SUM(CASE WHEN COALESCE(ss.available_kg, 0) <
                                              COALESCE(ss.low_stock_threshold, :defaultThreshold)
                                         THEN 1 ELSE 0 END), 0) AS low_count
                FROM seed_types st
                LEFT JOIN seed_stock ss ON ss.seed_type_id = st.id AND ss.shop_id = :shop
                WHERE st.active = 1""",
                p.addValue("defaultThreshold", SeedStockService.DEFAULT_LOW_THRESHOLD));
        return new DashboardDtos.SeedStock(
                qty((BigDecimal) row.get("total_kg")),
                ((Number) row.get("seed_types")).longValue(),
                ((Number) row.get("low_count")).longValue());
    }

    private DashboardDtos.ExpiringSoon expiringSoon(MapSqlParameterSource p, LocalDate today) {
        List<DashboardDtos.ExpiringBatch> all = db.jdbc().query("""
                SELECT b.id, pr.name, b.batch_id, b.qty_available,
                       COALESCE(b.expiry_date, b.best_before_date) AS expiry_date,
                       DATEDIFF(COALESCE(b.expiry_date, b.best_before_date), :today) AS days_left
                FROM product_batch_stock b
                JOIN products pr ON pr.id = b.product_id
                WHERE b.shop_id = :shop AND b.active = 1 AND b.qty_available > 0
                  AND COALESCE(b.expiry_date, b.best_before_date) IS NOT NULL
                  AND COALESCE(b.expiry_date, b.best_before_date) <= :soon
                  AND NOT EXISTS (SELECT 1 FROM expiry_actions a
                                  WHERE a.batch_stock_id = b.id AND a.action_type = 'SNOOZE'
                                    AND a.snoozed_until >= :today)
                ORDER BY days_left""", p, (rs, i) -> new DashboardDtos.ExpiringBatch(
                rs.getLong("id"), rs.getString("name"), rs.getString("batch_id"),
                qty(rs.getBigDecimal("qty_available")), JdbcSupport.date(rs, "expiry_date"),
                rs.getLong("days_left")));
        BigDecimal atRisk = all.stream().map(DashboardDtos.ExpiringBatch::qtyAvailable)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        LocalDate nearest = all.isEmpty() ? null : all.get(0).expiryDate();
        return new DashboardDtos.ExpiringSoon(all.size(), nearest, qty(atRisk),
                all.subList(0, Math.min(TOP_N, all.size())));
    }

    private DashboardDtos.MoneyToCollect moneyToCollect(MapSqlParameterSource p) {
        Map<String, Object> row = db.jdbc().queryForMap("""
                SELECT COALESCE(SUM(due), 0) AS amount,
                       COALESCE(SUM(CASE WHEN kind = 'BILL' THEN 1 ELSE 0 END), 0) AS unpaid_bills,
                       COALESCE(SUM(CASE WHEN kind = 'PARTY' THEN 1 ELSE 0 END), 0) AS parties
                FROM (
                    SELECT 'BILL' AS kind, s.total_amount - s.paid_amount AS due
                    FROM sales s
                    WHERE s.shop_id = :shop AND s.is_void = 0 AND s.active = 1
                      AND s.payment_status <> 'PAID' AND (s.total_amount - s.paid_amount) > 0
                      AND NOT EXISTS (SELECT 1 FROM khata_entries e
                                      WHERE e.entry_type = 'CREDIT_SALE' AND e.source_type = 'SALE'
                                        AND e.source_id = s.id)
                    UNION ALL
                    SELECT 'PARTY' AS kind, kp.opening_balance + COALESCE(SUM(ke.delta), 0) AS due
                    FROM khata_parties kp LEFT JOIN khata_entries ke ON ke.party_id = kp.id
                    WHERE kp.shop_id = :shop AND kp.active = 1
                    GROUP BY kp.id, kp.opening_balance
                    HAVING kp.opening_balance + COALESCE(SUM(ke.delta), 0) > 0
                ) t""", p);
        return new DashboardDtos.MoneyToCollect(
                money((BigDecimal) row.get("amount")),
                ((Number) row.get("unpaid_bills")).longValue(),
                ((Number) row.get("parties")).longValue());
    }

    private DashboardDtos.LicenceAlerts licenceAlerts(MapSqlParameterSource p, LocalDate today) {
        Map<String, Object> counts = db.jdbc().queryForMap("""
                SELECT COALESCE(SUM(status = 'EXPIRING_SOON'), 0) AS expiring_soon,
                       COALESCE(SUM(status = 'EXPIRED'), 0) AS expired
                FROM licences WHERE shop_id = :shop AND deleted_at IS NULL""", p);
        List<DashboardDtos.LicenceAlert> next = db.jdbc().query("""
                SELECT id, licence_number, type, expiry_date,
                       DATEDIFF(expiry_date, :today) AS days_left, status
                FROM licences
                WHERE shop_id = :shop AND deleted_at IS NULL AND status <> 'ACTIVE'
                ORDER BY expiry_date, id LIMIT 1""", p, (rs, i) -> new DashboardDtos.LicenceAlert(
                rs.getLong("id"), rs.getString("licence_number"), rs.getString("type"),
                JdbcSupport.date(rs, "expiry_date"), rs.getLong("days_left"), rs.getString("status")));

        return new DashboardDtos.LicenceAlerts(
                ((Number) counts.get("expiring_soon")).longValue(),
                ((Number) counts.get("expired")).longValue(),
                next.isEmpty() ? null : next.get(0));
    }

    private DashboardDtos.RefillDue refillDue(MapSqlParameterSource p, LocalDate today) {
        List<DashboardDtos.RefillCustomer> all = db.jdbc().query("""
                SELECT id, name, phone, predicted_refill_date,
                       DATEDIFF(predicted_refill_date, :today) AS days_until
                FROM customers
                WHERE shop_id = :shop AND active = 1 AND predicted_refill_date IS NOT NULL
                  AND predicted_refill_date <= :refillDue
                ORDER BY predicted_refill_date, name""", p, (rs, i) -> new DashboardDtos.RefillCustomer(
                rs.getLong("id"), rs.getString("name"), rs.getString("phone"),
                JdbcSupport.date(rs, "predicted_refill_date"), rs.getLong("days_until")));
        return new DashboardDtos.RefillDue(all.size(), all.subList(0, Math.min(TOP_N, all.size())));
    }

    // ==================== charts ====================

    /**
     * One point per day for the last 30 days, zero-filled so a quiet day still draws a point rather
     * than stretching the line across it. Bills and litres come from their own aggregate and are
     * merged here, keeping a join from multiplying either one.
     */
    private List<DashboardDtos.SalesPoint> salesSeries(MapSqlParameterSource p, LocalDate today, LocalDate from) {
        Map<LocalDate, BigDecimal[]> byDay = new HashMap<>();
        db.jdbc().query("""
                SELECT DATE(created_at) AS d, COUNT(*) AS bills, COALESCE(SUM(total_amount), 0) AS amount
                FROM sales
                WHERE shop_id = :shop AND is_void = 0 AND active = 1
                  AND created_at >= :seriesStart AND created_at < :seriesEnd
                GROUP BY DATE(created_at)""", p, rs -> {
            byDay.put(JdbcSupport.date(rs, "d"),
                    new BigDecimal[]{rs.getBigDecimal("amount"), BigDecimal.ZERO, BigDecimal.valueOf(rs.getLong("bills"))});
        });
        db.jdbc().query("""
                SELECT DATE(s.created_at) AS d,
                       COALESCE(SUM(CASE WHEN si.unit = 'L' THEN si.qty
                                         WHEN si.unit = 'ML' THEN si.qty / 1000
                                         ELSE 0 END), 0) AS litres
                FROM sale_items si JOIN sales s ON s.id = si.sale_id
                WHERE si.shop_id = :shop AND s.is_void = 0 AND s.active = 1
                  AND s.created_at >= :seriesStart AND s.created_at < :seriesEnd
                GROUP BY DATE(s.created_at)""", p, rs -> {
            BigDecimal[] slot = byDay.get(JdbcSupport.date(rs, "d"));
            if (slot != null) {
                slot[1] = rs.getBigDecimal("litres");
            }
        });

        List<DashboardDtos.SalesPoint> points = new ArrayList<>(SERIES_DAYS);
        for (LocalDate day = from; !day.isAfter(today); day = day.plusDays(1)) {
            BigDecimal[] slot = byDay.getOrDefault(day, new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO});
            points.add(new DashboardDtos.SalesPoint(day, money(slot[0]), slot[2].longValue(), qty(slot[1])));
        }
        return points;
    }

    private List<DashboardDtos.OilMixSlice> oilMix(MapSqlParameterSource p) {
        return db.jdbc().query("""
                SELECT pr.oil_type_id,
                       COALESCE(o.name_en, CASE WHEN pr.is_khali = 1 THEN 'Khali' ELSE 'Other' END) AS label,
                       COALESCE(o.colour_tag, 'brand') AS colour_tag,
                       COALESCE(SUM(si.amount), 0) AS amount
                FROM sale_items si
                JOIN sales s ON s.id = si.sale_id
                JOIN products pr ON pr.id = si.product_id
                LEFT JOIN oil_types o ON o.id = pr.oil_type_id
                WHERE si.shop_id = :shop AND s.is_void = 0 AND s.active = 1
                  AND s.created_at >= :seriesStart AND s.created_at < :seriesEnd
                GROUP BY pr.oil_type_id, o.name_en, pr.is_khali, o.colour_tag
                ORDER BY amount DESC""", p, (rs, i) -> {
            long oilTypeId = rs.getLong("oil_type_id");
            boolean noOil = rs.wasNull();
            return new DashboardDtos.OilMixSlice(noOil ? null : oilTypeId, rs.getString("label"),
                    rs.getString("colour_tag"), money(rs.getBigDecimal("amount")));
        });
    }

    // ==================== profit ====================

    private record Profit(BigDecimal net, BigDecimal margin) {
    }

    /** Reuses the P&amp;L engine so the card and the P&amp;L screen are the same arithmetic. */
    private Profit monthNetProfit(Long shopId, LocalDate today) {
        ExpenseDtos.PnLSummaryResponse pnl = profitLossService
                .getProfitLoss(shopId, today.withDayOfMonth(1), today, "month");
        return new Profit(pnl.netProfit(), pnl.netMarginPercent());
    }

    // ==================== helpers ====================

    private static BigDecimal money(BigDecimal value) {
        return value == null ? ZERO : value.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal qty(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(3) : value.setScale(3, RoundingMode.HALF_UP);
    }
}