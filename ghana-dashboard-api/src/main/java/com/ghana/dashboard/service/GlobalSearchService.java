package com.ghana.dashboard.service;

import com.ghana.dashboard.common.JdbcSupport;
import com.ghana.dashboard.dto.SearchDtos;
import com.ghana.dashboard.dto.SearchDtos.Group;
import com.ghana.dashboard.dto.SearchDtos.SearchGroup;
import com.ghana.dashboard.dto.SearchDtos.SearchItem;
import com.ghana.dashboard.dto.SearchDtos.SearchResponse;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One box that finds anything in the shop.
 *
 * <p>Every query is shop-scoped and reads the same tables the corresponding screens read, so a hit
 * always lands on a page that can actually show the row. Results are capped per group: the palette
 * is a shortcut, not a report, and a customer called "Sharma" should not push a product out of
 * sight. Six rows are fetched per group only to learn whether there was a seventh.
 */
@Service
public class GlobalSearchService {

    /** How many results a group shows before it is cut off. */
    static final int PER_GROUP = 5;

    /** Fetched instead of {@link #PER_GROUP} so "hasMore" is knowable without a second query. */
    private static final int FETCH = PER_GROUP + 1;

    /** Long enough for a bill number or a name, short enough to reject pasted paragraphs. */
    private static final int MAX_QUERY = 64;

    private final JdbcSupport db;

    public GlobalSearchService(JdbcSupport db) {
        this.db = db;
    }

    @Transactional(readOnly = true)
    public SearchResponse search(Long shopId, String rawQuery) {
        String query = rawQuery == null ? "" : rawQuery.trim();
        if (query.isEmpty()) {
            return new SearchResponse("", List.of(), 0);
        }
        if (query.length() > MAX_QUERY) {
            query = query.substring(0, MAX_QUERY);
        }
        // Wildcards inside the user's own text are escaped so a search for "50%" is a search for
        // "50%" and not a match-everything pattern.
        String like = "%" + escapeLike(query) + "%";
        MapSqlParameterSource p = params(shopId, like);

        Map<Group, List<SearchItem>> found = new LinkedHashMap<>();
        List<SearchItem> bills = billNumbers(p);
        found.put(Group.BILL_NUMBER, bills);
        // A bill number already reads well in the promoted group. Running the same match again
        // under Sales would just show the identical row twice, so those two groups are skipped
        // whenever the bill-number group found something - a partial bill number matches in both.
        found.put(Group.PRODUCTS, products(p));
        found.put(Group.SALES, bills.isEmpty() ? sales(p) : List.of());
        found.put(Group.ORDERS, bills.isEmpty() ? orders(p) : List.of());
        found.put(Group.CUSTOMERS, customers(p));
        found.put(Group.KHATA_PARTIES, khataParties(p));
        found.put(Group.BATCHES, batches(p));
        found.put(Group.SUPPLIERS, suppliers(p));

        List<SearchGroup> groups = new ArrayList<>();
        int total = 0;
        for (Map.Entry<Group, List<SearchItem>> entry : found.entrySet()) {
            List<SearchItem> items = entry.getValue();
            if (items.isEmpty()) {
                continue;
            }
            boolean hasMore = items.size() > PER_GROUP;
            List<SearchItem> visible = hasMore ? items.subList(0, PER_GROUP) : items;
            total += visible.size();
            groups.add(new SearchGroup(entry.getKey().name(), List.copyOf(visible), hasMore));
        }
        return new SearchResponse(query, groups, total);
    }

    // ==================== per-group queries ====================

    /** Exact bill/order number matches, promoted above everything else. */
    private List<SearchItem> billNumbers(MapSqlParameterSource p) {
        List<SearchItem> items = new ArrayList<>();
        items.addAll(db.jdbc().query("""
                SELECT s.id, s.bill_no, s.total_amount, s.created_at, s.is_void
                FROM sales s
                WHERE s.shop_id = :shop AND s.bill_no LIKE :like
                ORDER BY (s.bill_no = :exact) DESC, s.created_at DESC LIMIT :fetch""",
                p,
                (rs, i) -> new SearchItem(rs.getLong("id"),
                        rs.getString("bill_no") + (rs.getBoolean("is_void") ? " (void)" : ""),
                        "Sale - " + money(rs.getBigDecimal("total_amount")),
                        "/sales?bill=" + rs.getString("bill_no"))));
        items.addAll(db.jdbc().query("""
                SELECT o.id, o.bill_no, o.total_amount, o.created_at, o.status
                FROM orders o
                WHERE o.shop_id = :shop AND o.bill_no LIKE :like
                ORDER BY (o.bill_no = :exact) DESC, o.created_at DESC LIMIT :fetch""",
                p,
                (rs, i) -> new SearchItem(rs.getLong("id"), rs.getString("bill_no"),
                        "Order - " + rs.getString("status") + " - " + money(rs.getBigDecimal("total_amount")),
                        "/orders?order=" + rs.getLong("id"))));
        return items;
    }

    private List<SearchItem> products(MapSqlParameterSource p) {
        return db.jdbc().query("""
                SELECT pr.id, pr.name, pr.sku, pr.selling_price,
                       COALESCE(SUM(b.qty_available), 0) AS available
                FROM products pr LEFT JOIN product_batch_stock b ON b.product_id = pr.id
                WHERE pr.shop_id = :shop AND pr.active = 1
                  AND (pr.name LIKE :like ESCAPE '\\\\' OR pr.sku LIKE :like ESCAPE '\\\\')
                GROUP BY pr.id, pr.name, pr.sku, pr.selling_price
                ORDER BY (pr.name = :exact) DESC, name LIMIT :fetch""",
                p,
                (rs, i) -> new SearchItem(rs.getLong("id"), rs.getString("name"),
                        "SKU " + rs.getString("sku") + " - " + money(rs.getBigDecimal("selling_price"))
                                + " - " + qty(rs.getBigDecimal("available")) + " in stock",
                        "/inventory?product=" + rs.getLong("id")));
    }

    private List<SearchItem> sales(MapSqlParameterSource p) {
        return db.jdbc().query("""
                SELECT s.id, s.bill_no, s.total_amount, s.created_at, s.payment_status, s.is_void, c.name
                FROM sales s LEFT JOIN customers c ON c.id = s.customer_id
                WHERE s.shop_id = :shop AND s.bill_no <> ''
                  AND (s.bill_no LIKE :like ESCAPE '\\\\' OR c.name LIKE :like ESCAPE '\\\\')
                ORDER BY s.created_at DESC LIMIT :fetch""",
                p,
                (rs, i) -> new SearchItem(rs.getLong("id"),
                        rs.getString("bill_no") + (rs.getBoolean("is_void") ? " (void)" : ""),
                        (rs.getString("name") == null ? "Walk-in" : rs.getString("name"))
                                + " - " + money(rs.getBigDecimal("total_amount"))
                                + " - " + rs.getString("payment_status")
                                + " - " + date(rs, "created_at"),
                        "/sales?bill=" + rs.getString("bill_no")));
    }

    private List<SearchItem> orders(MapSqlParameterSource p) {
        return db.jdbc().query("""
                SELECT o.id, o.bill_no, o.total_amount, o.status, o.created_at, c.name
                FROM orders o LEFT JOIN customers c ON c.id = o.customer_id
                WHERE o.shop_id = :shop AND o.active = 1
                  AND (o.bill_no LIKE :like ESCAPE '\\\\' OR c.name LIKE :like ESCAPE '\\\\')
                ORDER BY o.created_at DESC LIMIT :fetch""",
                p,
                (rs, i) -> new SearchItem(rs.getLong("id"), rs.getString("bill_no"),
                        (rs.getString("name") == null ? "Walk-in" : rs.getString("name"))
                                + " - " + rs.getString("status") + " - "
                                + money(rs.getBigDecimal("total_amount")),
                        "/orders?order=" + rs.getLong("id")));
    }

    private List<SearchItem> customers(MapSqlParameterSource p) {
        return db.jdbc().query("""
                SELECT id, name, phone FROM customers
                WHERE shop_id = :shop AND active = 1
                  AND (name LIKE :like ESCAPE '\\\\' OR phone LIKE :like ESCAPE '\\\\')
                ORDER BY (name = :exact) DESC, name LIMIT :fetch""",
                p,
                (rs, i) -> new SearchItem(rs.getLong("id"), rs.getString("name"), rs.getString("phone"),
                        "/customers?customer=" + rs.getLong("id")));
    }

    private List<SearchItem> khataParties(MapSqlParameterSource p) {
        return db.jdbc().query("""
                SELECT id, name, phone, type FROM khata_parties
                WHERE shop_id = :shop AND active = 1
                  AND (name LIKE :like ESCAPE '\\\\' OR phone LIKE :like ESCAPE '\\\\')
                ORDER BY (name = :exact) DESC, name LIMIT :fetch""",
                p,
                (rs, i) -> new SearchItem(rs.getLong("id"), rs.getString("name"),
                        "Khata " + rs.getString("type").toLowerCase(java.util.Locale.ROOT)
                                + " - " + rs.getString("phone"),
                        "/khata?party=" + rs.getLong("id")));
    }

    /** Stock lots, found by the printed batch code or by the product they belong to. */
    private List<SearchItem> batches(MapSqlParameterSource p) {
        return db.jdbc().query("""
                SELECT b.id, b.batch_id, b.expiry_date, b.qty_available, pr.id AS product_id, pr.name
                FROM product_batch_stock b JOIN products pr ON pr.id = b.product_id
                WHERE b.shop_id = :shop AND b.active = 1
                  AND (b.batch_id LIKE :like ESCAPE '\\\\' OR pr.name LIKE :like ESCAPE '\\\\')
                ORDER BY (b.batch_id = :exact) DESC, b.expiry_date LIMIT :fetch""",
                p,
                (rs, i) -> {
                    // An unlabelled adjustment lot has no batch code, so fall back to the product.
                    String batchId = rs.getString("batch_id");
                    return new SearchItem(rs.getLong("id"),
                            batchId == null ? rs.getString("name") : batchId,
                            rs.getString("name") + " - " + qty(rs.getBigDecimal("qty_available"))
                                    + (rs.getDate("expiry_date") == null ? ""
                                    : " - expires " + rs.getDate("expiry_date")),
                            "/inventory?batch=" + rs.getLong("id"));
                });
    }

    private List<SearchItem> suppliers(MapSqlParameterSource p) {
        return db.jdbc().query("""
                SELECT id, name, phone, gstin FROM suppliers
                WHERE shop_id = :shop AND active = 1
                  AND (name LIKE :like ESCAPE '\\\\' OR phone LIKE :like ESCAPE '\\\\'
                       OR gstin LIKE :like ESCAPE '\\\\')
                ORDER BY (name = :exact) DESC, name LIMIT :fetch""",
                p,
                (rs, i) -> new SearchItem(rs.getLong("id"), rs.getString("name"),
                        rs.getString("phone") == null ? "Supplier" : rs.getString("phone")
                                + (rs.getString("gstin") == null ? "" : " - GSTIN " + rs.getString("gstin")),
                        "/beej/suppliers?supplier=" + rs.getLong("id")));
    }

    // ==================== helpers ====================

    /**
     * Every query needs the same four parameters: the shop, the contains-pattern, the same text
     * without its wildcards for exact-match ranking, and the row cap.
     */
    private MapSqlParameterSource params(Long shopId, String like) {
        return new MapSqlParameterSource()
                .addValue("shop", shopId)
                .addValue("like", like)
                .addValue("exact", like.replace("%", ""))
                .addValue("fetch", FETCH);
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static String date(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        Timestamp ts = rs.getTimestamp(column);
        return ts == null ? "" : ts.toLocalDateTime().toLocalDate().toString();
    }

    private static String money(java.math.BigDecimal value) {
        return value == null ? "0" : value.stripTrailingZeros().toPlainString();
    }

    private static String qty(java.math.BigDecimal value) {
        return value == null ? "0" : value.stripTrailingZeros().toPlainString();
    }
}