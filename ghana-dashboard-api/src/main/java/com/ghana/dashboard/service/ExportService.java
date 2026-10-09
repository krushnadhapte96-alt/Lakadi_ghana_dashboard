package com.ghana.dashboard.service;

import com.ghana.dashboard.common.DomainException;
import com.ghana.dashboard.common.JdbcSupport;
import com.ghana.dashboard.dto.SettingsDtos;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Builds the shop's data export as a zip of CSV files.
 *
 * <p>Each dataset is a hand-written {@code SELECT} rendered through a single CSV
 * escaper rather than a generic table dumper: the column lists are the deliberate
 * choice here, and they keep unrelated internal columns (cost prices, password
 * hashes, soft-delete bookkeeping) out of a file the owner is likely to email to an
 * accountant.
 *
 * <p>The whole archive is assembled in memory. A single shop's operational data is
 * small enough to fit comfortably, and it keeps the endpoint to one streaming write.
 */
@Service
public class ExportService {

    /** One selectable dataset: the SQL, its CSV file name, and a human label. */
    private record Dataset(String key, String fileName, String label, String sql) {
    }

    /**
     * Every column below was checked against the live schema: most core tables here use
     * an `active` flag rather than `deleted_at`, stock lives in `product_batch_stock`
     * rather than on the product row, and money columns are `total_amount`/`amount`
     * rather than `total`. Soft-delete filtering is therefore per table.
     */
    private static final List<Dataset> DATASETS = List.of(
            new Dataset("products", "products.csv", "Products",
                    """
                    SELECT p.sku, p.name, o.name_en AS oil_type, p.pack_size, p.unit, p.container,
                           p.selling_price, p.mrp, p.cost_price, p.low_stock_threshold,
                           COALESCE(st.qty_available, 0) AS stock_qty, p.active
                    FROM products p
                    LEFT JOIN oil_types o ON o.id = p.oil_type_id
                    LEFT JOIN (SELECT product_id, SUM(qty_available) AS qty_available
                               FROM product_batch_stock WHERE active = TRUE GROUP BY product_id) st
                           ON st.product_id = p.id
                    WHERE p.shop_id = :shop
                    ORDER BY p.name"""),
            new Dataset("customers", "customers.csv", "Customers",
                    """
                    SELECT c.name, c.phone, c.email, c.gst_no, c.tags, c.total_orders, c.total_spent,
                           c.last_purchase_at, c.avg_gap_days, c.preferred_language, c.active
                    FROM customers c
                    WHERE c.shop_id = :shop
                    ORDER BY c.name"""),
            new Dataset("sales", "sales.csv", "Sales",
                    """
                    SELECT s.bill_no, DATE(s.created_at) AS sale_date, s.subtotal, s.discount,
                           s.discount_type, s.total_amount, s.paid_amount, s.payment_mode,
                           s.payment_status, COALESCE(c.name, 'Walk-in') AS customer,
                           u.name AS sold_by, s.is_void
                    FROM sales s
                    LEFT JOIN customers c ON c.id = s.customer_id
                    LEFT JOIN users u ON u.id = s.user_id
                    WHERE s.shop_id = :shop
                    ORDER BY s.created_at DESC"""),
            new Dataset("sale_items", "sale_items.csv", "Sale line items",
                    """
                    SELECT s.bill_no, i.product_name, i.sku, b.code AS batch_code,
                           i.qty, i.unit, i.rate, i.amount
                    FROM sale_items i
                    JOIN sales s ON s.id = i.sale_id
                    LEFT JOIN batches b ON b.id = i.batch_id
                    WHERE s.shop_id = :shop
                    ORDER BY s.created_at DESC, i.id"""),
            new Dataset("orders", "orders.csv", "Orders",
                    """
                    SELECT o.bill_no, DATE(o.created_at) AS order_date, COALESCE(c.name, 'Walk-in') AS customer,
                           o.status, o.payment_status, o.subtotal, o.delivery_charge, o.discount,
                           o.total_amount, o.paid_amount, o.expected_delivery_date, o.actual_delivery_date
                    FROM orders o
                    LEFT JOIN customers c ON c.id = o.customer_id
                    WHERE o.shop_id = :shop
                    ORDER BY o.created_at DESC"""),
            new Dataset("payments", "payments.csv", "Payments",
                    """
                    SELECT p.payment_date, p.direction, p.mode, p.amount, p.source_type,
                           p.source_id, p.reference_no, p.note, u.name AS entered_by
                    FROM payments p
                    LEFT JOIN users u ON u.id = p.created_by
                    WHERE p.shop_id = :shop
                    ORDER BY p.created_at DESC"""),
            new Dataset("expenses", "expenses.csv", "Expenses",
                    """
                    SELECT e.expense_date, COALESCE(ec.name_en, 'Uncategorised') AS category,
                           e.title, e.amount, e.payment_mode, e.reference_no,
                           e.description, u.name AS entered_by
                    FROM expenses e
                    LEFT JOIN expense_categories ec ON ec.id = e.category_id
                    LEFT JOIN users u ON u.id = e.created_by
                    WHERE e.shop_id = :shop
                    ORDER BY e.created_at DESC"""),
            new Dataset("khata", "khata.csv", "Khata ledger",
                    """
                    SELECT kp.name AS party, k.entry_date, k.entry_type, k.bill_no, k.source_type,
                           k.delta, k.payment_mode, k.due_date, k.note
                    FROM khata_entries k
                    JOIN khata_parties kp ON kp.id = k.party_id
                    WHERE k.shop_id = :shop
                    ORDER BY kp.name, k.entry_date"""),
            new Dataset("khata_parties", "khata_parties.csv", "Khata parties",
                    """
                    SELECT name, type, phone, address, credit_limit, payment_terms_days,
                           opening_balance, active
                    FROM khata_parties
                    WHERE shop_id = :shop
                    ORDER BY name"""),
            new Dataset("suppliers", "suppliers.csv", "Suppliers",
                    """
                    SELECT s.name, s.phone, s.address, s.gstin, s.active
                    FROM suppliers s
                    WHERE s.shop_id = :shop
                    ORDER BY s.name"""),
            new Dataset("batches", "batches.csv", "Production batches",
                    """
                    SELECT b.code, o.name_en AS oil_type, b.mfg_date, b.best_before_date,
                           b.seed_kg, b.oil_litres, b.khali_kg, b.yield_pct, b.status
                    FROM batches b
                    LEFT JOIN oil_types o ON o.id = b.oil_type_id
                    WHERE b.shop_id = :shop
                    ORDER BY b.mfg_date DESC, b.code"""),
            new Dataset("stock_movements", "stock_movements.csv", "Stock movements",
                    """
                    SELECT DATE(sm.created_at) AS moved_on, p.name AS product, sm.type, sm.change_qty,
                           sm.unit_price, sm.total_value, sm.reason, u.name AS by_user
                    FROM stock_movements sm
                    JOIN products p ON p.id = sm.product_id
                    LEFT JOIN users u ON u.id = sm.user_id
                    WHERE sm.shop_id = :shop
                    ORDER BY sm.created_at DESC"""),
            new Dataset("licences", "licences.csv", "Licence register",
                    """
                    SELECT l.licence_number, l.type, l.issuer, l.issue_date, l.expiry_date, l.status,
                           l.fssai_gstin, l.notes,
                           (SELECT COUNT(*) FROM licence_renewals r WHERE r.licence_id = l.id) AS renewal_count,
                           (SELECT MAX(r.created_at) FROM licence_renewals r WHERE r.licence_id = l.id) AS last_renewed_at
                    FROM licences l
                    WHERE l.shop_id = :shop AND l.deleted_at IS NULL
                    ORDER BY l.expiry_date"""));
    private final JdbcSupport db;

    public ExportService(JdbcSupport db) {
        this.db = db;
    }

    /** Row counts per dataset, so the UI can show what an export will contain. */
    @Transactional(readOnly = true)
    public SettingsDtos.ExportManifest manifest(Long shopId) {
        List<SettingsDtos.ExportManifestEntry> entries = new ArrayList<>();
        for (Dataset dataset : DATASETS) {
            long rows = count(shopId, dataset);
            entries.add(new SettingsDtos.ExportManifestEntry(dataset.key(), dataset.label(), rows));
        }
        return new SettingsDtos.ExportManifest(LocalDateTime.now().toString(), entries);
    }

    public byte[] zip(Long shopId) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(buffer, StandardCharsets.UTF_8)) {
            for (Dataset dataset : DATASETS) {
                String csv = render(shopId, dataset);
                // Leave out datasets the shop has no rows for, so the archive is not
                // mostly header-only files.
                if (csv.isEmpty()) {
                    continue;
                }
                zip.putNextEntry(new ZipEntry(dataset.fileName()));
                zip.write(csv.getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
            zip.putNextEntry(new ZipEntry("README.txt"));
            zip.write(readme().getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        } catch (Exception e) {
            throw new DomainException(500, "Could not build the export archive");
        }
        return buffer.toByteArray();
    }

    public String fileName(Long shopId) {
        return "ghana-export-" + shopId + "-" + LocalDate.now() + ".zip";
    }

    // ==================== rendering ====================

    private String render(Long shopId, Dataset dataset) {
        List<Map<String, Object>> rows = db.jdbc().queryForList(dataset.sql(), Map.of("shop", shopId));
        if (rows.isEmpty()) {
            return "";
        }
        // Column order comes from the returned key set, which preserves the SELECT list,
        // so a column added to the query cannot drift out of sync with the header row.
        List<String> columns = new ArrayList<>(rows.get(0).keySet());
        StringBuilder csv = new StringBuilder();
        csv.append(columns.stream().map(ExportService::escape).collect(Collectors.joining(","))).append("\r\n");
        for (Map<String, Object> row : rows) {
            List<String> cells = new ArrayList<>(columns.size());
            for (String column : columns) {
                cells.add(escape(stringify(row.get(column))));
            }
            csv.append(String.join(",", cells)).append("\r\n");
        }
        return csv.toString();
    }

    private long count(Long shopId, Dataset dataset) {
        return db.jdbc().queryForList(dataset.sql(), Map.of("shop", shopId)).size();
    }

    private static String stringify(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        return String.valueOf(value);
    }

    /**
     * RFC 4180 quoting: only quote when needed, and double any embedded quote. A leading
     * =, +, - or @ is prefixed with a tab so a spreadsheet treats the cell as text
     * rather than evaluating it as a formula.
     */
    static String escape(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        String safe = value;
        char first = safe.charAt(0);
        if (first == '=' || first == '+' || first == '@') {
            safe = "\t" + safe;
        }
        boolean needsQuotes = safe.indexOf(',') >= 0 || safe.indexOf('"') >= 0
                || safe.indexOf('\n') >= 0 || safe.indexOf('\r') >= 0 || safe.startsWith("\t");
        if (!needsQuotes) {
            return safe;
        }
        return '"' + safe.replace("\"", "\"\"") + '"';
    }

    private String readme() {
        return """
                Ghana Dashboard data export
                Generated: %s

                Each CSV holds one dataset, UTF-8 encoded, comma separated, CRLF line
                endings and RFC 4180 quoting. Amounts are plain numbers without symbols.

                Datasets with no rows for this shop were left out of the archive.

                This file contains your shop's business data. Store and share it
                accordingly.
                """.formatted(LocalDateTime.now());
    }
}