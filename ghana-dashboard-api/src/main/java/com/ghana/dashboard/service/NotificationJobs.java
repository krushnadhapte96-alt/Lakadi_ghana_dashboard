package com.ghana.dashboard.service;

import com.ghana.dashboard.common.DateDefaults;
import com.ghana.dashboard.common.JdbcSupport;
import com.ghana.dashboard.dto.CustomerDtos;
import com.ghana.dashboard.dto.ExpiryAlertDtos;
import com.ghana.dashboard.dto.KhataDtos;
import com.ghana.dashboard.dto.LicenceDtos;
import com.ghana.dashboard.dto.NotificationDtos.JobResult;
import com.ghana.dashboard.dto.NotificationDtos.NewNotification;
import com.ghana.dashboard.dto.NotificationDtos.NotificationType;
import com.ghana.dashboard.dto.NotificationDtos.Severity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/**
 * The ten checks that decide what a shop needs to be told about.
 *
 * <p>Every method is idempotent and safe to call by hand, which is what the dev trigger endpoint
 * relies on: running a sweep twice must not double the bell. That guarantee comes from
 * {@link NotificationService}'s de-duplication rather than from any bookkeeping here, so the jobs
 * only have to decide <em>what</em> is worth saying, and let the service decide whether it was
 * already said today.
 *
 * <p>The two high-traffic alerts also fire from events - see {@link NotificationEventListener} for
 * the low-stock and new-order paths, which react the moment something happens instead of waiting
 * for the next sweep. The job versions exist so the dev trigger, and the recovery sweep, see the
 * same thing.
 */
@Service
public class NotificationJobs {

    private static final Logger log = LoggerFactory.getLogger(NotificationJobs.class);

    private final NotificationService notifications;
    private final JdbcSupport db;
    private final ExpiryAlertsService expiryAlerts;
    private final LicenceService licences;
    private final KhataService khata;
    private final CustomerInsightService insights;
    private final CashBookDayService cashBook;

    public NotificationJobs(NotificationService notifications, JdbcSupport db,
                            ExpiryAlertsService expiryAlerts, LicenceService licences,
                            KhataService khata, CustomerInsightService insights,
                            CashBookDayService cashBook) {
        this.notifications = notifications;
        this.db = db;
        this.expiryAlerts = expiryAlerts;
        this.licences = licences;
        this.khata = khata;
        this.insights = insights;
        this.cashBook = cashBook;
    }

    // ==================== the checks ====================

    /**
     * Mirrors the dashboard's low-stock card exactly - available + reserved below the product's
     * own threshold - so the bell and the home screen never tell two different stories.
     */
    public JobResult lowStock(Long shopId) {
        List<LowStockRow> rows = db.jdbc().query("""
                SELECT pr.id, pr.name, pr.sku,
                       COALESCE(SUM(b.qty_available + b.qty_reserved), 0) AS available,
                       pr.low_stock_threshold AS threshold
                FROM products pr LEFT JOIN product_batch_stock b ON b.product_id = pr.id
                WHERE pr.shop_id = :shop AND pr.active = 1
                GROUP BY pr.id, pr.name, pr.sku, pr.low_stock_threshold
                HAVING available < threshold
                ORDER BY available, pr.name""",
                new MapSqlParameterSource("shop", shopId),
                (rs, i) -> new LowStockRow(rs.getLong("id"), rs.getString("name"), rs.getString("sku"),
                        rs.getBigDecimal("available"), rs.getBigDecimal("threshold")));

        int created = 0;
        for (LowStockRow row : rows) {
            boolean written = notifications.create(new NewNotification(
                    shopId, null, NotificationType.LOW_STOCK, Severity.WARNING,
                    "Low stock: " + row.name(),
                    "Only " + trim(row.available()) + " left, threshold is " + trim(row.threshold()) + ".",
                    "/inventory?filter=low&product=" + row.id(),
                    "LOW_STOCK:" + row.id()));
            created += written ? 1 : 0;
        }
        return tally(NotificationType.LOW_STOCK, rows.size(), created);
    }

    /**
     * New orders normally announce themselves through {@code OrderPlacedEvent} the moment they are
     * placed. This sweep catches the ones that never got that far - a notification written while the
     * app was down, or a row whose event was rolled back with the rest of the transaction.
     */
    public JobResult newOrder(Long shopId) {
        List<OrderRow> rows = db.jdbc().query("""
                SELECT o.id, o.bill_no, o.total_amount, o.expected_delivery_date, o.created_at, c.name,
                       (SELECT COUNT(*) FROM order_items oi WHERE oi.order_id = o.id) AS item_count
                FROM orders o LEFT JOIN customers c ON c.id = o.customer_id
                WHERE o.shop_id = :shop AND o.active = 1 AND o.status <> 'CANCELLED'
                  AND o.created_at >= :since
                ORDER BY o.created_at DESC""",
                new MapSqlParameterSource("shop", shopId)
                        .addValue("since", Timestamp.valueOf(DateDefaults.today().atStartOfDay())),
                (rs, i) -> new OrderRow(rs.getLong("id"), rs.getString("bill_no"),
                        rs.getBigDecimal("total_amount"), rs.getString("name"),
                        rs.getInt("item_count"),
                        JdbcSupport.date(rs, "expected_delivery_date")));

        int created = 0;
        for (OrderRow row : rows) {
            String customer = row.customerName() == null || row.customerName().isBlank()
                    ? "Walk-in customer" : row.customerName();
            boolean written = notifications.create(new NewNotification(
                    shopId, null, NotificationType.NEW_ORDER, Severity.INFO,
                    "New order " + row.billNo(),
                    customer + " placed " + row.itemCount() + " item(s)"
                            + (row.total() == null ? "" : " for " + trim(row.total())) + ".",
                    "/orders?order=" + row.id(),
                    "NEW_ORDER:" + row.id()));
            created += written ? 1 : 0;
        }
        return tally(NotificationType.NEW_ORDER, rows.size(), created);
    }

    /** An order past its promised delivery date that has not been delivered or cancelled. */
    public JobResult orderOverdue(Long shopId) {
        LocalDate today = DateDefaults.today();
        List<OverdueOrderRow> rows = db.jdbc().query("""
                SELECT o.id, o.bill_no, o.expected_delivery_date, c.name, o.total_amount
                FROM orders o LEFT JOIN customers c ON c.id = o.customer_id
                WHERE o.shop_id = :shop AND o.active = 1
                  AND o.status NOT IN ('DELIVERED', 'CANCELLED')
                  AND o.expected_delivery_date IS NOT NULL AND o.expected_delivery_date < :today
                ORDER BY o.expected_delivery_date""",
                new MapSqlParameterSource("shop", shopId).addValue("today", today),
                (rs, i) -> new OverdueOrderRow(rs.getLong("id"), rs.getString("bill_no"),
                        JdbcSupport.date(rs, "expected_delivery_date"), rs.getString("name"),
                        rs.getBigDecimal("total_amount")));

        int created = 0;
        for (OverdueOrderRow row : rows) {
            long late = today.toEpochDay() - row.expected().toEpochDay();
            String customer = row.customerName() == null ? "customer" : row.customerName();
            boolean written = notifications.create(new NewNotification(
                    shopId, null, NotificationType.ORDER_OVERDUE, Severity.WARNING,
                    "Order " + row.billNo() + " is overdue",
                    "Delivery was due on " + row.expected() + " - " + late + " day(s) late for "
                            + customer + ".",
                    "/orders?order=" + row.id(),
                    "ORDER_OVERDUE:" + row.id()));
            created += written ? 1 : 0;
        }
        return tally(NotificationType.ORDER_OVERDUE, rows.size(), created);
    }

    /**
     * Reuses the expiry screen's own bucketing rather than re-deriving "soon": if the two ever
     * disagreed, an operator would get a bell entry the expiry page does not show.
     */
    public JobResult expiry(Long shopId) {
        List<ExpiryAlertDtos.ExpiryAlertItemResponse> items =
                expiryAlerts.alertableItems(shopId, DateDefaults.today());
        int created = 0;
        for (ExpiryAlertDtos.ExpiryAlertItemResponse item : items) {
            Severity severity = item.daysLeft() < 0 ? Severity.CRITICAL : Severity.WARNING;
            String headline = item.daysLeft() < 0
                    ? "Expired " + Math.abs(item.daysLeft()) + " day(s) ago: " + item.productName()
                    : "Expiring in " + item.daysLeft() + " day(s): " + item.productName();
            boolean written = notifications.create(new NewNotification(
                    shopId, null, NotificationType.EXPIRY, severity, headline,
                    "Batch " + item.batchId() + " holds " + trim(item.qtyAvailable())
                            + " worth " + trim(item.valueAtRisk()) + ".",
                    "/expiry?batch=" + item.batchStockId(),
                    "EXPIRY:" + item.batchStockId()));
            created += written ? 1 : 0;
        }
        return tally(NotificationType.EXPIRY, items.size(), created);
    }

    /** Licence warnings, straight off the licence summary the licences screen already shows. */
    public JobResult licence(Long shopId) {
        LicenceDtos.LicenceSummaryResponse summary = licences.summary(shopId);
        int candidates = (int) Math.max(summary.expired(), 0);
        List<String> missing = summary.missingTypes();
        StringBuilder message = new StringBuilder();
        if (summary.expired() > 0) {
            message.append(summary.expired()).append(" licence(s) already expired");
        }
        if (!missing.isEmpty()) {
            if (message.length() > 0) {
                message.append(". ");
            }
            message.append("Missing registration: ").append(String.join(", ", missing));
        }
        if (message.length() == 0) {
            return tally(NotificationType.LICENCE, 0, 0);
        }
        String next = summary.nextExpiry() == null ? "no date set" : summary.nextExpiry().toString();
        boolean written = notifications.create(new NewNotification(
                shopId, null, NotificationType.LICENCE,
                summary.expired() > 0 ? Severity.CRITICAL : Severity.WARNING,
                "Licence check needs attention",
                message + ". Next expiry: " + next + ".",
                "/licences",
                "LICENCE:shop"));
        return tally(NotificationType.LICENCE, Math.max(candidates, 1), written ? 1 : 0);
    }

    /** Khata parties whose credit has run past their agreed terms. */
    public JobResult udhaarOverdue(Long shopId) {
        List<KhataDtos.PartyResponse> parties = khata.listParties(shopId, null, Boolean.TRUE,
                Boolean.TRUE, null);
        int created = 0;
        for (KhataDtos.PartyResponse party : parties) {
            BigDecimal amount = party.overdueAmount() == null ? party.balance() : party.overdueAmount();
            if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            boolean written = notifications.create(new NewNotification(
                    shopId, null, NotificationType.UDHAAR_OVERDUE, Severity.WARNING,
                    "Udhaar overdue: " + party.name(),
                    trim(amount) + " outstanding" + (party.overdueDays() > 0
                            ? ", " + party.overdueDays() + " day(s) past terms." : "."),
                    "/khata?party=" + party.id(),
                    "UDHAAR_OVERDUE:" + party.id()));
            created += written ? 1 : 0;
        }
        return tally(NotificationType.UDHAAR_OVERDUE, parties.size(), created);
    }

    /**
     * Seed stock, on the same basis as the dashboard's seed card: every active catalogue entry
     * counts, and a type the shop has never bought is zero kg rather than absent.
     */
    public JobResult seedLow(Long shopId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("shop", shopId)
                .addValue("defaultThreshold", SeedStockService.DEFAULT_LOW_THRESHOLD);
        List<SeedRow> rows = db.jdbc().query("""
                SELECT st.id, st.name_en AS name, COALESCE(ss.available_kg, 0) AS available_kg,
                       COALESCE(ss.low_stock_threshold, :defaultThreshold) AS threshold
                FROM seed_types st
                LEFT JOIN seed_stock ss ON ss.seed_type_id = st.id AND ss.shop_id = :shop
                WHERE st.active = 1 AND COALESCE(ss.available_kg, 0)
                                      < COALESCE(ss.low_stock_threshold, :defaultThreshold)
                ORDER BY available_kg, st.name_en""",
                params, (rs, i) -> new SeedRow(rs.getLong("id"), rs.getString("name"),
                        rs.getBigDecimal("available_kg"), rs.getBigDecimal("threshold")));

        int created = 0;
        for (SeedRow row : rows) {
            boolean written = notifications.create(new NewNotification(
                    shopId, null, NotificationType.SEED_LOW, Severity.WARNING,
                    "Seed low: " + row.name(),
                    trim(row.availableKg()) + " kg in stock against a " + trim(row.threshold()) + " kg threshold.",
                    "/beej/stock?seedType=" + row.id(),
                    "SEED_LOW:" + row.id()));
            created += written ? 1 : 0;
        }
        return tally(NotificationType.SEED_LOW, rows.size(), created);
    }

    /**
     * What the shop owes its suppliers. Purchases track what was paid as they are made, so the
     * outstanding figure is derived here rather than stored - there is no balance column to drift.
     */
    public JobResult supplierDue(Long shopId) {
        List<SupplierDueRow> rows = db.jdbc().query("""
                SELECT s.id, s.name, SUM(sp.total_amount - sp.amount_paid) AS due
                FROM seed_purchases sp
                JOIN suppliers s ON s.id = sp.supplier_id
                WHERE sp.shop_id = :shop AND s.active = 1 AND sp.payment_status <> 'PAID'
                GROUP BY s.id, s.name
                HAVING due > 0
                ORDER BY due DESC""",
                new MapSqlParameterSource("shop", shopId),
                (rs, i) -> new SupplierDueRow(rs.getLong("id"), rs.getString("name"),
                        rs.getBigDecimal("due")));

        int created = 0;
        for (SupplierDueRow row : rows) {
            boolean written = notifications.create(new NewNotification(
                    shopId, null, NotificationType.SUPPLIER_DUE, Severity.WARNING,
                    "Payable to " + row.name(),
                    trim(row.due()) + " outstanding across unpaid seed purchases.",
                    "/beej/suppliers?supplier=" + row.id(),
                    "SUPPLIER_DUE:" + row.id()));
            created += written ? 1 : 0;
        }
        return tally(NotificationType.SUPPLIER_DUE, rows.size(), created);
    }

    /** Regulars whose predicted refill date is close - a nudge, not an emergency. */
    public JobResult refillDue(Long shopId) {
        List<CustomerDtos.ListItem> customers = insights.list(shopId, "refill", null);
        int created = 0;
        for (CustomerDtos.ListItem customer : customers) {
            if (!customer.refillDue()) {
                continue;
            }
            String when = customer.predictedRefillDate() == null
                    ? "based on past visits" : "expected around " + customer.predictedRefillDate();
            boolean written = notifications.create(new NewNotification(
                    shopId, null, NotificationType.REFILL_DUE, Severity.INFO,
                    "Refill due: " + customer.name(),
                    customer.phone() == null || customer.phone().isBlank()
                            ? "Repeat customer " + when + "."
                            : customer.phone() + " - repeat customer " + when + ".",
                    "/customers?customer=" + customer.id(),
                    "REFILL_DUE:" + customer.id()));
            created += written ? 1 : 0;
        }
        return tally(NotificationType.REFILL_DUE, customers.size(), created);
    }

    /**
     * The cash book day is still open at 22:00. Worth shouting about: an unclosed day leaves the
     * day's cash unverified, and this is the one alert that means money may be unaccounted for.
     */
    public JobResult dayNotClosed(Long shopId) {
        LocalDate today = DateDefaults.today();
        if (cashBook.isDayClosed(shopId, today)) {
            return tally(NotificationType.DAY_NOT_CLOSED, 0, 0);
        }
        boolean written = notifications.create(new NewNotification(
                shopId, null, NotificationType.DAY_NOT_CLOSED, Severity.CRITICAL,
                "Cash book day not closed",
                "The cash book for " + today + " is still open. Close the day and check the cash count.",
                "/payments?tab=cashbook&date=" + today,
                "DAY_NOT_CLOSED:" + today));
        return tally(NotificationType.DAY_NOT_CLOSED, 1, written ? 1 : 0);
    }

    // ==================== dispatch ====================

    /** Runs one named job. Used by the dev trigger endpoint and the scheduler. */
    public JobResult run(String jobName, Long shopId) {
        NotificationType type = parse(jobName);
        return switch (type) {
            case LOW_STOCK -> lowStock(shopId);
            case NEW_ORDER -> newOrder(shopId);
            case ORDER_OVERDUE -> orderOverdue(shopId);
            case EXPIRY -> expiry(shopId);
            case LICENCE -> licence(shopId);
            case UDHAAR_OVERDUE -> udhaarOverdue(shopId);
            case SEED_LOW -> seedLow(shopId);
            case SUPPLIER_DUE -> supplierDue(shopId);
            case REFILL_DUE -> refillDue(shopId);
            case DAY_NOT_CLOSED -> dayNotClosed(shopId);
        };
    }

    /** Runs every check. Used by the daily sweep. */
    public List<JobResult> runAll(Long shopId) {
        return List.of(lowStock(shopId), newOrder(shopId), orderOverdue(shopId), expiry(shopId),
                licence(shopId), udhaarOverdue(shopId), seedLow(shopId), supplierDue(shopId),
                refillDue(shopId), dayNotClosed(shopId));
    }

    /** Every job name, for the dev trigger's own help response. */
    public static List<String> jobNames() {
        return java.util.Arrays.stream(NotificationType.values()).map(Enum::name).toList();
    }

    private static NotificationType parse(String raw) {
        try {
            return NotificationType.valueOf(raw.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException e) {
            throw new com.ghana.dashboard.common.DomainException(400,
                    "Unknown job '" + raw + "'. Known jobs: " + String.join(", ", jobNames()));
        }
    }

    private static JobResult tally(NotificationType type, int candidates, int created) {
        return new JobResult(type.name(), created, Math.max(candidates - created, 0));
    }

    private static String trim(BigDecimal value) {
        return value == null ? "0" : value.stripTrailingZeros().toPlainString();
    }

    private record LowStockRow(Long id, String name, String sku, BigDecimal available, BigDecimal threshold) {
    }

    private record OrderRow(Long id, String billNo, BigDecimal total, String customerName, int itemCount,
                            LocalDate expectedDelivery) {
    }

    private record OverdueOrderRow(Long id, String billNo, LocalDate expected, String customerName,
                                   BigDecimal total) {
    }

    private record SeedRow(Long id, String name, BigDecimal availableKg, BigDecimal threshold) {
    }

    private record SupplierDueRow(Long id, String name, BigDecimal due) {
    }
}