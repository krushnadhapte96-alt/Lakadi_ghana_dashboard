package com.ghana.dashboard.service;

import com.ghana.dashboard.common.DateDefaults;
import com.ghana.dashboard.common.DomainException;
import com.ghana.dashboard.common.JdbcSupport;
import com.ghana.dashboard.domain.Role;
import com.ghana.dashboard.dto.KhataDtos;
import com.ghana.dashboard.event.SaleOnCreditEvent;
import com.ghana.dashboard.event.SaleVoidedEvent;
import com.ghana.dashboard.security.CurrentUser;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Low-level khata ledger operations: party lookup, balance and overdue maths, the credit-limit guard
 * and the listeners that turn udhaar sales/orders into ledger entries. It depends on nothing but the
 * database so the sale and order services can use it without creating a dependency cycle.
 */
@Service
public class KhataLedgerService {

    private final JdbcSupport db;

    public KhataLedgerService(JdbcSupport db) {
        this.db = db;
    }

    // ==================== Balance maths ====================

    public record PartyRow(Long id, Long shopId, Long customerId, String name, String type, String phone,
                           String address, BigDecimal creditLimit, int termsDays, BigDecimal openingBalance,
                           boolean active, LocalDateTime createdAt) {
    }

    public record EntryRow(Long id, String type, BigDecimal amount, BigDecimal delta, LocalDate entryDate,
                           LocalDate dueDate) {
    }

    public record Standing(BigDecimal balance, BigDecimal overdue, int overdueDays, LocalDate dueDate,
                           LocalDate lastPayment) {
    }

    /** Payments and negative adjustments are applied to the oldest dues first (FIFO). */
    public static Standing standing(PartyRow party, List<EntryRow> entries, LocalDate today) {
        BigDecimal balance = party.openingBalance();
        BigDecimal pool = BigDecimal.ZERO;
        LocalDate lastPayment = null;
        List<LocalDate> dueDates = new ArrayList<>();
        List<BigDecimal> owed = new ArrayList<>();

        if (party.openingBalance().signum() > 0) {
            dueDates.add(party.createdAt().toLocalDate().plusDays(party.termsDays()));
            owed.add(party.openingBalance());
        } else if (party.openingBalance().signum() < 0) {
            pool = pool.add(party.openingBalance().negate());
        }
        for (EntryRow e : entries) {
            balance = balance.add(e.delta());
            if (e.delta().signum() > 0) {
                dueDates.add(e.dueDate() != null ? e.dueDate() : e.entryDate().plusDays(party.termsDays()));
                owed.add(e.delta());
            } else if (e.delta().signum() < 0) {
                pool = pool.add(e.delta().negate());
                if ("PAYMENT_RECEIVED".equals(e.type()) && (lastPayment == null || e.entryDate().isAfter(lastPayment))) {
                    lastPayment = e.entryDate();
                }
            }
        }

        Integer[] order = new Integer[owed.size()];
        for (int i = 0; i < order.length; i++) {
            order[i] = i;
        }
        Arrays.sort(order, Comparator.comparing(dueDates::get));

        BigDecimal overdue = BigDecimal.ZERO;
        LocalDate oldestOverdue = null;
        LocalDate nextDue = null;
        for (Integer idx : order) {
            BigDecimal remaining = owed.get(idx);
            BigDecimal used = pool.min(remaining);
            pool = pool.subtract(used);
            remaining = remaining.subtract(used);
            if (remaining.signum() <= 0) {
                continue;
            }
            LocalDate due = dueDates.get(idx);
            if (nextDue == null) {
                nextDue = due;
            }
            if (due.isBefore(today)) {
                overdue = overdue.add(remaining);
                if (oldestOverdue == null) {
                    oldestOverdue = due;
                }
            }
        }
        int overdueDays = oldestOverdue == null ? 0 : (int) ChronoUnit.DAYS.between(oldestOverdue, today);
        return new Standing(balance, overdue, overdueDays, balance.signum() > 0 ? nextDue : null, lastPayment);
    }

    // ==================== Lookups ====================

    public Optional<PartyRow> findParty(Long shopId, Long partyId) {
        return db.jdbc().query("SELECT * FROM khata_parties WHERE id = :id AND shop_id = :shop",
                Map.of("id", partyId, "shop", shopId), (rs, i) -> mapParty(rs)).stream().findFirst();
    }

    public PartyRow requireParty(Long shopId, Long partyId) {
        return findParty(shopId, partyId).orElseThrow(() -> new DomainException(404, "Khata party not found"));
    }

    public Optional<PartyRow> findPartyByPhone(Long shopId, String phone) {
        return db.jdbc().query("SELECT * FROM khata_parties WHERE shop_id = :shop AND phone = :phone",
                Map.of("shop", shopId, "phone", phone), (rs, i) -> mapParty(rs)).stream().findFirst();
    }

    public List<PartyRow> listParties(Long shopId) {
        return db.jdbc().query("SELECT * FROM khata_parties WHERE shop_id = :shop ORDER BY name",
                Map.of("shop", shopId), (rs, i) -> mapParty(rs));
    }

    public List<EntryRow> entriesOf(Long partyId) {
        return db.jdbc().query("""
                SELECT id, entry_type, amount, delta, entry_date, due_date FROM khata_entries
                WHERE party_id = :party ORDER BY entry_date, id""", Map.of("party", partyId),
                (rs, i) -> new EntryRow(rs.getLong("id"), rs.getString("entry_type"), rs.getBigDecimal("amount"),
                        rs.getBigDecimal("delta"), JdbcSupport.date(rs, "entry_date"), JdbcSupport.date(rs, "due_date")));
    }

    /** One query for all entries of a shop, grouped by party - avoids N+1 on the party list. */
    public Map<Long, List<EntryRow>> entriesByParty(Long shopId) {
        Map<Long, List<EntryRow>> grouped = new HashMap<>();
        db.jdbc().query("""
                SELECT party_id, id, entry_type, amount, delta, entry_date, due_date FROM khata_entries
                WHERE shop_id = :shop ORDER BY entry_date, id""", Map.of("shop", shopId), rs -> {
            grouped.computeIfAbsent(rs.getLong("party_id"), k -> new ArrayList<>()).add(new EntryRow(
                    rs.getLong("id"), rs.getString("entry_type"), rs.getBigDecimal("amount"),
                    rs.getBigDecimal("delta"), JdbcSupport.date(rs, "entry_date"), JdbcSupport.date(rs, "due_date")));
        });
        return grouped;
    }

    public Standing standingOf(PartyRow party) {
        return standing(party, entriesOf(party.id()), DateDefaults.today());
    }

    private static PartyRow mapParty(java.sql.ResultSet rs) throws java.sql.SQLException {
        long customer = rs.getLong("customer_id");
        boolean noCustomer = rs.wasNull();
        return new PartyRow(rs.getLong("id"), rs.getLong("shop_id"), noCustomer ? null : customer,
                rs.getString("name"), rs.getString("type"), rs.getString("phone"), rs.getString("address"),
                rs.getBigDecimal("credit_limit"), rs.getInt("payment_terms_days"), rs.getBigDecimal("opening_balance"),
                rs.getBoolean("active"), JdbcSupport.dateTime(rs, "created_at"));
    }

    // ==================== Credit limit guard ====================

    /**
     * Throws 409 when taking {@code amount} more credit would push the party over its limit. Only the
     * OWNER may override; the 409 message tells the client an override is possible.
     */
    public void assertCreditAllowed(Long shopId, String phone, BigDecimal amount, boolean override) {
        if (phone == null) {
            return;
        }
        Optional<PartyRow> party = findPartyByPhone(shopId, phone);
        if (party.isEmpty() || party.get().creditLimit() == null) {
            return;
        }
        PartyRow p = party.get();
        BigDecimal projected = standingOf(p).balance().add(amount);
        if (projected.compareTo(p.creditLimit()) <= 0) {
            return;
        }
        if (override) {
            if (CurrentUser.getRole() != Role.OWNER) {
                throw new DomainException(403, "Only the owner can override a credit limit");
            }
            return;
        }
        throw new DomainException(409, "Credit limit exceeded for " + p.name() + ": limit Rs. " + p.creditLimit()
                + ", balance after this sale would be Rs. " + projected + ". The owner can override.");
    }

    // ==================== Ledger writes ====================

    public long insertEntry(Long shopId, Long partyId, String type, BigDecimal amount, BigDecimal delta,
                            LocalDate entryDate, LocalDate dueDate, String sourceType, Long sourceId, String billNo,
                            Long paymentId, String paymentMode, String note, Long userId) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("shop_id", shopId);
        row.put("party_id", partyId);
        row.put("entry_type", type);
        row.put("amount", amount);
        row.put("delta", delta);
        row.put("entry_date", java.sql.Date.valueOf(entryDate));
        row.put("due_date", dueDate == null ? null : java.sql.Date.valueOf(dueDate));
        row.put("source_type", sourceType);
        row.put("source_id", sourceId);
        row.put("bill_no", billNo);
        row.put("payment_id", paymentId);
        row.put("payment_mode", paymentMode);
        row.put("note", note);
        row.put("created_by", userId);
        row.put("created_at", Timestamp.valueOf(LocalDateTime.now()));
        return db.insert("khata_entries", row);
    }

    @Transactional
    public PartyRow ensureParty(Long shopId, Long customerId, String name, String phone) {
        return findPartyByPhone(shopId, phone).orElseGet(() -> {
            LocalDateTime now = LocalDateTime.now();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("shop_id", shopId);
            row.put("customer_id", customerId);
            row.put("name", name == null || name.isBlank() ? "Customer " + phone : name);
            row.put("type", "INDIVIDUAL");
            row.put("phone", phone);
            row.put("payment_terms_days", 30);
            row.put("opening_balance", BigDecimal.ZERO);
            row.put("active", true);
            row.put("created_at", Timestamp.valueOf(now));
            row.put("updated_at", Timestamp.valueOf(now));
            long id = db.insert("khata_parties", row);
            return requireParty(shopId, id);
        });
    }

    /** Re-allocates received payments across the party's udhaar sales so each bill shows its true status. */
    public void syncSaleStatuses(PartyRow party) {
        BigDecimal pool = db.jdbc().queryForObject("""
                SELECT COALESCE(SUM(-delta), 0) FROM khata_entries
                WHERE party_id = :party AND delta < 0""", Map.of("party", party.id()), BigDecimal.class);
        if (pool == null) {
            return;
        }
        if (party.openingBalance().signum() > 0) {
            pool = pool.subtract(party.openingBalance()).max(BigDecimal.ZERO);
        }
        List<Map<String, Object>> sales = db.jdbc().queryForList("""
                SELECT e.source_id AS sale_id, e.amount AS amount FROM khata_entries e
                WHERE e.party_id = :party AND e.entry_type = 'CREDIT_SALE' AND e.source_type = 'SALE'
                ORDER BY e.entry_date, e.id""", Map.of("party", party.id()));
        for (Map<String, Object> sale : sales) {
            BigDecimal amount = (BigDecimal) sale.get("amount");
            BigDecimal paid = pool.min(amount);
            pool = pool.subtract(paid);
            String status = paid.compareTo(amount) >= 0 ? "PAID" : paid.signum() > 0 ? "PARTIAL" : "UNPAID";
            db.jdbc().update("UPDATE sales SET paid_amount = :paid, payment_status = :status WHERE id = :id",
                    new MapSqlParameterSource().addValue("paid", paid).addValue("status", status)
                            .addValue("id", sale.get("sale_id")));
        }
    }

    // ==================== Event listeners ====================

    @EventListener
    @Transactional
    public void onCreditSale(SaleOnCreditEvent event) {
        if (event.getCustomerPhone() == null || event.getCustomerPhone().isBlank()) {
            return;
        }
        Long shopId = event.getShopId();
        Integer exists = db.jdbc().queryForObject("""
                SELECT COUNT(*) FROM khata_entries WHERE entry_type = 'CREDIT_SALE'
                AND source_type = :type AND source_id = :id""",
                Map.of("type", event.getTransactionType(), "id", event.getTransactionId()), Integer.class);
        if (exists != null && exists > 0) {
            return;
        }
        PartyRow party = ensureParty(shopId, event.getCustomerId(), event.getCustomerName(), event.getCustomerPhone());
        LocalDate date = event.getCreatedAt().toLocalDate();
        long entryId = insertEntry(shopId, party.id(), "CREDIT_SALE", event.getTotalAmount(), event.getTotalAmount(),
                date, date.plusDays(party.termsDays()), event.getTransactionType(), event.getTransactionId(),
                event.getBillNo(), null, null,
                "Udhaar " + event.getTransactionType().toLowerCase() + " " + event.getBillNo(), null);
        boolean sale = "SALE".equals(event.getTransactionType());
        db.jdbc().update("INSERT INTO khata_entry_items (entry_id, shop_id, product_id, product_name, qty, unit, rate, amount) "
                + "SELECT :entry, shop_id, product_id, product_name, qty, unit, rate, amount FROM "
                + (sale ? "sale_items WHERE sale_id" : "order_items WHERE order_id") + " = :source",
                Map.of("entry", entryId, "source", event.getTransactionId()));
    }

    @EventListener
    @Transactional
    public void onSaleVoided(SaleVoidedEvent event) {
        Map<String, Object> key = Map.of("type", event.transactionType(), "id", event.transactionId());
        List<Long> parties = db.jdbc().queryForList("""
                SELECT DISTINCT party_id FROM khata_entries
                WHERE entry_type = 'CREDIT_SALE' AND source_type = :type AND source_id = :id""", key, Long.class);
        db.jdbc().update("DELETE FROM khata_entries WHERE entry_type = 'CREDIT_SALE' AND source_type = :type AND source_id = :id", key);
        for (Long partyId : parties) {
            findParty(event.shopId(), partyId).ifPresent(this::syncSaleStatuses);
        }
    }

    public static KhataDtos.PartyResponse toResponse(PartyRow p, Standing s) {
        BigDecimal available = p.creditLimit() == null ? null : p.creditLimit().subtract(s.balance()).max(BigDecimal.ZERO);
        return new KhataDtos.PartyResponse(p.id(), p.customerId(), p.name(), p.type(), p.phone(), p.address(),
                p.creditLimit(), p.termsDays(), p.openingBalance(), p.active(), s.balance(), s.overdue(),
                s.overdue().signum() > 0, s.overdueDays(), s.dueDate(), s.lastPayment(), available,
                JdbcSupport.iso(p.createdAt()));
    }
}
