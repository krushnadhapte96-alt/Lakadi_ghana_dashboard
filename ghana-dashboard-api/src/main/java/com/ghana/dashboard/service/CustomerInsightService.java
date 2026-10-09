package com.ghana.dashboard.service;

import com.ghana.dashboard.common.DateDefaults;
import com.ghana.dashboard.common.DomainException;
import com.ghana.dashboard.common.JdbcSupport;
import com.ghana.dashboard.dto.CustomerDtos;
import com.ghana.dashboard.dto.KhataDtos;
import com.ghana.dashboard.dto.SalesDtos;
import com.ghana.dashboard.event.CustomerActivityEvent;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Customer 360: purchase statistics, refill prediction, notes, history and the WhatsApp refill nudge. */
@Service
public class CustomerInsightService {

    /** A customer counts as "refill due" from this many days before the predicted date. */
    static final int REFILL_LEAD_DAYS = 3;
    static final int INACTIVE_DAYS = 60;
    private static final int TOP_LIMIT = 20;

    @PersistenceContext
    private EntityManager entityManager;

    private final JdbcSupport db;
    private final CustomerService customerService;
    private final ShopService shopService;

    public CustomerInsightService(JdbcSupport db, CustomerService customerService, ShopService shopService) {
        this.db = db;
        this.customerService = customerService;
        this.shopService = shopService;
    }

    // ==================== Statistics ====================

    public record Prediction(BigDecimal avgGapDays, LocalDate predictedDate) {
    }

    /** predicted refill = last purchase + average gap between distinct purchase days (needs 2+ purchases). */
    public static Prediction predict(List<LocalDate> purchaseDays) {
        List<LocalDate> days = purchaseDays.stream().distinct().sorted().toList();
        if (days.size() < 2) {
            return new Prediction(null, null);
        }
        long span = ChronoUnit.DAYS.between(days.get(0), days.get(days.size() - 1));
        BigDecimal avg = BigDecimal.valueOf(span).divide(BigDecimal.valueOf(days.size() - 1), 1, RoundingMode.HALF_UP);
        LocalDate predicted = days.get(days.size() - 1).plusDays(avg.setScale(0, RoundingMode.HALF_UP).longValue());
        return new Prediction(avg, predicted);
    }

    @EventListener
    @Transactional
    public void onActivity(CustomerActivityEvent event) {
        if (event.customerId() != null) {
            entityManager.flush();
            recompute(event.shopId(), event.customerId());
        }
    }

    @Transactional
    public void recompute(Long shopId, Long customerId) {
        Map<String, Object> p = Map.of("c", customerId, "shop", shopId);
        List<LocalDate> days = db.jdbc().query("""
                SELECT d FROM (
                    SELECT DATE(created_at) AS d FROM sales WHERE customer_id = :c AND is_void = 0 AND total_amount > 0
                    UNION
                    SELECT COALESCE(actual_delivery_date, DATE(created_at)) FROM orders WHERE customer_id = :c AND status = 'DELIVERED'
                ) t ORDER BY d""", p, (rs, i) -> JdbcSupport.date(rs, "d"));

        Integer sales = db.jdbc().queryForObject(
                "SELECT COUNT(*) FROM sales WHERE customer_id = :c AND is_void = 0 AND total_amount > 0", p, Integer.class);
        Integer orders = db.jdbc().queryForObject(
                "SELECT COUNT(*) FROM orders WHERE customer_id = :c AND status = 'DELIVERED'", p, Integer.class);
        BigDecimal spent = db.jdbc().queryForObject("""
                SELECT (SELECT COALESCE(SUM(total_amount), 0) FROM sales WHERE customer_id = :c AND is_void = 0)
                     + (SELECT COALESCE(SUM(total_amount), 0) FROM orders WHERE customer_id = :c AND status = 'DELIVERED')
                     - (SELECT COALESCE(SUM(refund_amount), 0) FROM returns
                        WHERE customer_id = :c AND resolution = 'REFUND' AND status = 'RESOLVED')""", p, BigDecimal.class);
        List<Timestamp> lastRows = db.jdbc().query("""
                SELECT MAX(at) AS at FROM (
                    SELECT created_at AS at FROM sales WHERE customer_id = :c AND is_void = 0 AND total_amount > 0
                    UNION ALL
                    SELECT COALESCE(CAST(actual_delivery_date AS DATETIME), created_at) FROM orders
                    WHERE customer_id = :c AND status = 'DELIVERED') t""", p, (rs, i) -> rs.getTimestamp("at"));
        List<Long> favourite = db.jdbc().queryForList("""
                SELECT product_id FROM (
                    SELECT si.product_id, SUM(si.qty) AS q FROM sale_items si JOIN sales s ON s.id = si.sale_id
                    WHERE s.customer_id = :c AND s.is_void = 0 GROUP BY si.product_id
                    UNION ALL
                    SELECT oi.product_id, SUM(oi.qty) FROM order_items oi JOIN orders o ON o.id = oi.order_id
                    WHERE o.customer_id = :c AND o.status = 'DELIVERED' GROUP BY oi.product_id) t
                GROUP BY product_id ORDER BY SUM(q) DESC LIMIT 1""", p, Long.class);

        Prediction prediction = predict(days);
        Timestamp last = lastRows.isEmpty() ? null : lastRows.get(0);
        db.jdbc().update("""
                UPDATE customers SET total_orders = :orders, total_spent = :spent, last_purchase_at = :last,
                avg_gap_days = :gap, predicted_refill_date = :predicted, favourite_product_id = :fav
                WHERE id = :c AND shop_id = :shop""", new MapSqlParameterSource()
                .addValue("orders", (sales == null ? 0 : sales) + (orders == null ? 0 : orders))
                .addValue("spent", spent == null ? BigDecimal.ZERO : spent.max(BigDecimal.ZERO))
                .addValue("last", last).addValue("gap", prediction.avgGapDays())
                .addValue("predicted", prediction.predictedDate() == null ? null : java.sql.Date.valueOf(prediction.predictedDate()))
                .addValue("fav", favourite.isEmpty() ? null : favourite.get(0))
                .addValue("c", customerId).addValue("shop", shopId));
    }

    // ==================== Lists ====================

    private static final String LIST_SELECT = """
            SELECT c.id, c.name, c.phone, c.tags, c.preferred_language, c.total_orders, c.total_spent,
                   c.last_purchase_at, c.predicted_refill_date, p.name AS fav_name
            FROM customers c LEFT JOIN products p ON p.id = c.favourite_product_id
            WHERE c.shop_id = :shop AND c.active = 1""";

    @Transactional(readOnly = true)
    public List<CustomerDtos.ListItem> list(Long shopId, String tab, String query) {
        LocalDate today = DateDefaults.today();
        StringBuilder sql = new StringBuilder(LIST_SELECT);
        MapSqlParameterSource params = new MapSqlParameterSource("shop", shopId);
        if (query != null && !query.isBlank()) {
            sql.append(" AND (c.name LIKE :q OR c.phone LIKE :q)");
            params.addValue("q", "%" + query.trim() + "%");
        }
        String mode = tab == null ? "all" : tab.toLowerCase(Locale.ROOT);
        switch (mode) {
            case "refill", "refilldue", "refill-due" -> {
                sql.append(" AND c.predicted_refill_date IS NOT NULL AND c.predicted_refill_date <= :due");
                sql.append(" ORDER BY c.predicted_refill_date, c.name");
                params.addValue("due", java.sql.Date.valueOf(today.plusDays(REFILL_LEAD_DAYS)));
            }
            case "top" -> sql.append(" AND c.total_spent > 0 ORDER BY c.total_spent DESC LIMIT ").append(TOP_LIMIT);
            case "inactive" -> {
                sql.append(" AND c.last_purchase_at IS NOT NULL AND c.last_purchase_at < :cutoff ORDER BY c.last_purchase_at");
                params.addValue("cutoff", Timestamp.valueOf(today.minusDays(INACTIVE_DAYS).atStartOfDay()));
            }
            default -> sql.append(" ORDER BY c.name");
        }
        return db.jdbc().query(sql.toString(), params, (rs, i) -> {
            Timestamp last = rs.getTimestamp("last_purchase_at");
            LocalDate predicted = JdbcSupport.date(rs, "predicted_refill_date");
            return new CustomerDtos.ListItem(rs.getLong("id"), rs.getString("name"), rs.getString("phone"),
                    splitTags(rs.getString("tags")), rs.getString("preferred_language"), rs.getInt("total_orders"),
                    rs.getBigDecimal("total_spent"), last == null ? null : last.toLocalDateTime().toString(),
                    last == null ? null : (int) ChronoUnit.DAYS.between(last.toLocalDateTime().toLocalDate(), today),
                    predicted, refillDue(predicted, today), rs.getString("fav_name"));
        });
    }

    static boolean refillDue(LocalDate predicted, LocalDate today) {
        return predicted != null && !predicted.isAfter(today.plusDays(REFILL_LEAD_DAYS));
    }

    // ==================== Detail ====================

    @Transactional(readOnly = true)
    public CustomerDtos.Detail detail(Long shopId, Long id) {
        LocalDate today = DateDefaults.today();
        List<Map<String, Object>> rows = db.jdbc().queryForList("""
                SELECT c.*, p.name AS fav_name FROM customers c LEFT JOIN products p ON p.id = c.favourite_product_id
                WHERE c.id = :id AND c.shop_id = :shop""", Map.of("id", id, "shop", shopId));
        if (rows.isEmpty()) {
            throw new DomainException(404, "Customer not found");
        }
        Map<String, Object> c = rows.get(0);
        LocalDateTime last = toLocalDateTime(c.get("last_purchase_at"));
        LocalDate predicted = toLocalDate(c.get("predicted_refill_date"));
        LocalDateTime created = toLocalDateTime(c.get("created_at"));

        List<CustomerDtos.NoteResponse> notes = db.jdbc().query("""
                SELECT n.id, n.note, n.created_at, u.name AS by_name FROM customer_notes n
                JOIN users u ON u.id = n.created_by WHERE n.customer_id = :id ORDER BY n.created_at DESC, n.id DESC""",
                Map.of("id", id), (rs, i) -> new CustomerDtos.NoteResponse(rs.getLong("id"), rs.getString("note"),
                        rs.getString("by_name"), JdbcSupport.iso(JdbcSupport.dateTime(rs, "created_at"))));

        List<CustomerDtos.HistoryItem> history = db.jdbc().query("""
                SELECT 'SALE' AS type, s.id, s.bill_no, s.created_at AS at, s.total_amount AS amount,
                       IF(s.is_void = 1, 'VOID', 'COMPLETED') AS status, s.payment_status,
                       (SELECT GROUP_CONCAT(CONCAT(si.product_name, ' x', TRIM(TRAILING '.' FROM TRIM(TRAILING '0' FROM si.qty)))
                               SEPARATOR ', ') FROM sale_items si WHERE si.sale_id = s.id) AS items
                FROM sales s WHERE s.customer_id = :id
                UNION ALL
                SELECT 'ORDER', o.id, o.bill_no, o.created_at, o.total_amount, o.status, o.payment_status,
                       (SELECT GROUP_CONCAT(CONCAT(oi.product_name, ' x', TRIM(TRAILING '.' FROM TRIM(TRAILING '0' FROM oi.qty)))
                               SEPARATOR ', ') FROM order_items oi WHERE oi.order_id = o.id)
                FROM orders o WHERE o.customer_id = :id
                ORDER BY at DESC LIMIT 50""", Map.of("id", id), (rs, i) -> new CustomerDtos.HistoryItem(
                rs.getString("type"), rs.getLong("id"), rs.getString("bill_no"),
                JdbcSupport.iso(JdbcSupport.dateTime(rs, "at")), rs.getBigDecimal("amount"), rs.getString("status"),
                rs.getString("payment_status"), rs.getString("items")));

        List<BigDecimal> khata = db.jdbc().queryForList("""
                SELECT p.opening_balance + COALESCE((SELECT SUM(e.delta) FROM khata_entries e WHERE e.party_id = p.id), 0)
                FROM khata_parties p WHERE p.shop_id = :shop AND p.customer_id = :id""", Map.of("shop", shopId, "id", id),
                BigDecimal.class);

        return new CustomerDtos.Detail(id, (String) c.get("name"), (String) c.get("phone"), (String) c.get("email"),
                (String) c.get("gst_no"), splitTags((String) c.get("tags")), (String) c.get("preferred_language"),
                Boolean.TRUE.equals(c.get("active")) || Integer.valueOf(1).equals(c.get("active")),
                created == null ? null : created.toString(),
                ((Number) c.get("total_orders")).intValue(), (BigDecimal) c.get("total_spent"),
                last == null ? null : last.toString(),
                last == null ? null : (int) ChronoUnit.DAYS.between(last.toLocalDate(), today),
                (BigDecimal) c.get("avg_gap_days"), predicted, refillDue(predicted, today),
                c.get("favourite_product_id") == null ? null : ((Number) c.get("favourite_product_id")).longValue(),
                (String) c.get("fav_name"), khata.isEmpty() ? null : khata.get(0),
                customerService.getCustomerAddresses(shopId, id), notes, history);
    }

    // ==================== Updates ====================

    @Transactional
    public CustomerDtos.Detail update(Long shopId, Long id, CustomerDtos.ProfileRequest request) {
        customerService.updateCustomer(shopId, id,
                new SalesDtos.CustomerRequest(request.name(), request.phone(), request.email(), request.gstNo()));
        String lang = "hi".equalsIgnoreCase(request.preferredLanguage()) ? "hi" : "en";
        db.jdbc().update("UPDATE customers SET tags = :tags, preferred_language = :lang WHERE id = :id AND shop_id = :shop",
                new MapSqlParameterSource().addValue("tags", joinTags(request.tags())).addValue("lang", lang)
                        .addValue("id", id).addValue("shop", shopId));
        return detail(shopId, id);
    }

    @Transactional
    public CustomerDtos.NoteResponse addNote(Long shopId, Long userId, Long customerId, String note) {
        customerService.getCustomer(shopId, customerId);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("shop_id", shopId);
        row.put("customer_id", customerId);
        row.put("note", note.trim());
        row.put("created_by", userId);
        row.put("created_at", Timestamp.valueOf(LocalDateTime.now()));
        long noteId = db.insert("customer_notes", row);
        return detail(shopId, customerId).notes().stream().filter(n -> n.id() == noteId).findFirst().orElseThrow();
    }

    // ==================== WhatsApp ====================

    @Transactional(readOnly = true)
    public KhataDtos.ReminderResponse whatsappRefill(Long shopId, Long customerId, String language) {
        CustomerDtos.Detail c = detail(shopId, customerId);
        String shop = shopService.findById(shopId).getName();
        boolean hindi = language == null ? "hi".equals(c.preferredLanguage()) : "hi".equalsIgnoreCase(language);
        String product = c.favouriteProductName();
        String message;
        if (hindi) {
            message = "à¤¨à¤®à¤¸à¥à¤¤à¥‡ " + c.name() + " à¤œà¥€,\n" + shop + " à¤•à¥€ à¤“à¤° à¤¸à¥‡ à¤¨à¤®à¤¸à¥à¤•à¤¾à¤°! "
                    + (product != null ? "à¤†à¤ªà¤•à¤¾ " + product + " " : "à¤†à¤ªà¤•à¤¾ à¤¤à¥‡à¤² ")
                    + (c.daysSinceLastPurchase() != null ? "à¤²à¤¿à¤ " + c.daysSinceLastPurchase() + " à¤¦à¤¿à¤¨ à¤¹à¥‹ à¤—à¤ à¤¹à¥ˆà¤‚à¥¤ " : "")
                    + "à¤•à¥à¤¯à¤¾ à¤¦à¥‹à¤¬à¤¾à¤°à¤¾ à¤®à¤‚à¤—à¤µà¤¾à¤¨à¤¾ à¤¹à¥ˆ? à¤¹à¤®à¥‡à¤‚ à¤¬à¤¤à¤¾à¤‡à¤, à¤¹à¤® à¤¤à¥à¤°à¤‚à¤¤ à¤­à¥‡à¤œ à¤¦à¥‡à¤‚à¤—à¥‡à¥¤";
        } else {
            message = "Namaste " + c.name() + ",\nGreetings from " + shop + "! "
                    + (c.daysSinceLastPurchase() != null ? "It has been " + c.daysSinceLastPurchase() + " days since your last purchase"
                    : "We would love to serve you again")
                    + (product != null ? " of " + product : "") + ". Time for a refill? Just reply and we will arrange it right away.";
        }
        return new KhataDtos.ReminderResponse(KhataService.waUrl(c.phone(), message), message, hindi ? "hi" : "en");
    }

    // ==================== helpers ====================

    static LocalDateTime toLocalDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDateTime dateTime) {
            return dateTime;
        }
        if (value instanceof Timestamp stamp) {
            return stamp.toLocalDateTime();
        }
        if (value instanceof java.util.Date date) {
            return new Timestamp(date.getTime()).toLocalDateTime();
        }
        throw new IllegalStateException("Unexpected datetime value type: " + value.getClass());
    }

    static LocalDate toLocalDate(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDate date) {
            return date;
        }
        if (value instanceof java.sql.Date date) {
            return date.toLocalDate();
        }
        if (value instanceof Timestamp stamp) {
            return stamp.toLocalDateTime().toLocalDate();
        }
        if (value instanceof java.util.Date date) {
            return new java.sql.Date(date.getTime()).toLocalDate();
        }
        throw new IllegalStateException("Unexpected date value type: " + value.getClass());
    }

    static List<String> splitTags(String tags) {
        if (tags == null || tags.isBlank()) {
            return List.of();
        }
        return Arrays.stream(tags.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    static String joinTags(List<String> tags) {
        if (tags == null) {
            return null;
        }
        List<String> clean = new ArrayList<>();
        for (String t : tags) {
            String v = t == null ? "" : t.trim().replace(",", " ");
            if (!v.isEmpty() && !clean.contains(v)) {
                clean.add(v);
            }
        }
        return clean.isEmpty() ? null : String.join(",", clean);
    }
}
