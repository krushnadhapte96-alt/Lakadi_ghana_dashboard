package com.ghana.dashboard.service;

import com.ghana.dashboard.common.DateDefaults;
import com.ghana.dashboard.common.DomainException;
import com.ghana.dashboard.common.JdbcSupport;
import com.ghana.dashboard.domain.LicenceStatus;
import com.ghana.dashboard.dto.LicenceDtos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Business licence register. Status is always derived from the expiry date — a
 * licence that nobody touches still flips to EXPIRING_SOON and then EXPIRED via
 * {@link LicenceScheduler#sweep()}, and the stored value is kept in step so the
 * list endpoint can filter on it. Renewing appends to {@code licence_renewals}
 * and moves the parent forward, so the history of a licence is never overwritten.
 */
@Service
public class LicenceService {

    private static final Logger log = LoggerFactory.getLogger(LicenceService.class);

    /** Inside this window a licence reads as EXPIRING_SOON rather than ACTIVE. */
    public static final int EXPIRING_SOON_DAYS = 30;
    /** Reminder thresholds, matching the sweep. */
    public static final List<Integer> REMINDER_DAYS = List.of(60, 30, 7);

    private static final Set<String> TYPES = Set.of("FSSAI", "GST", "SHOP_ESTABLISHMENT", "TRADE",
            "WEIGHING_SCALE_STAMPING", "FIRE_NOC", "OTHER");
    private static final String UPLOAD_FOLDER = "licences";

    private final JdbcSupport db;
    private final FileStorageService storage;

    public LicenceService(JdbcSupport db, FileStorageService storage) {
        this.db = db;
        this.storage = storage;
    }

    public record Document(Resource resource, String fileName, String contentType) {
    }

    /** The two registration numbers printed on bills. */
    public record BillIdentifiers(String fssaiNumber, String gstin) {
    }

    // ==================== status ====================

    /**
     * The one place a licence's status is decided. {@code daysLeft < 0} means the
     * licence has lapsed; today itself is still EXPIRING_SOON, not EXPIRED.
     */
    public static LicenceStatus statusFor(LocalDate expiryDate, LocalDate today) {
        long days = ChronoUnit.DAYS.between(today, expiryDate);
        if (days < 0) {
            return LicenceStatus.EXPIRED;
        }
        return days <= EXPIRING_SOON_DAYS ? LicenceStatus.EXPIRING_SOON : LicenceStatus.ACTIVE;
    }

    private static String statusExpression(String dateColumn) {
        return "CASE WHEN " + dateColumn + " < :today THEN 'EXPIRED' WHEN " + dateColumn
                + " <= :soon THEN 'EXPIRING_SOON' ELSE 'ACTIVE' END";
    }

    // ==================== CRUD ====================

    @Transactional
    public LicenceDtos.LicenceResponse create(Long shopId, Long userId, LicenceDtos.LicenceCreateRequest request,
                                              MultipartFile file) {
        String type = normalizeType(request.type());
        validateDates(request.issueDate(), request.expiryDate());
        LocalDateTime now = LocalDateTime.now();

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("shop_id", shopId);
        fill(row, type, request.licenceNumber(), request.issuer(), request.issueDate(), request.expiryDate(),
                request.fssaiGstin(), request.notes());
        row.put("status", statusFor(request.expiryDate(), DateDefaults.today()).name());
        row.put("created_by", userId);
        row.put("created_at", Timestamp.valueOf(now));
        row.put("updated_at", Timestamp.valueOf(now));

        FileStorageService.Stored stored = null;
        if (file != null && !file.isEmpty()) {
            stored = storage.store(shopId, UPLOAD_FOLDER, file, false);
            row.put("document_path", stored.path().toString());
            row.put("document_name", stored.originalName());
            row.put("document_type", stored.contentType());
        }
        try {
            return get(shopId, db.insert("licences", row));
        } catch (RuntimeException e) {
            if (stored != null) {
                storage.delete(stored.path());
            }
            throw rethrowDuplicate(e, request.licenceNumber());
        }
    }

    private static final String SELECT = """
            SELECT l.id, l.licence_number, l.type, l.issuer, l.issue_date, l.expiry_date, l.status,
                   l.fssai_gstin, l.document_path, l.document_name, l.document_type, l.notes,
                   l.created_at, l.updated_at,
                   (SELECT COUNT(*) FROM licence_renewals r WHERE r.licence_id = l.id) AS renewal_count,
                   (SELECT MAX(r.created_at) FROM licence_renewals r WHERE r.licence_id = l.id) AS last_renewed_at
            FROM licences l WHERE l.shop_id = :shop AND l.deleted_at IS NULL""";

    @Transactional(readOnly = true)
    public List<LicenceDtos.LicenceResponse> list(Long shopId, String status, String type) {
        StringBuilder sql = new StringBuilder(SELECT);
        MapSqlParameterSource params = new MapSqlParameterSource("shop", shopId);
        if (status != null && !status.isBlank()) {
            sql.append(" AND l.status = :status");
            params.addValue("status", normalizeStatus(status));
        }
        if (type != null && !type.isBlank()) {
            sql.append(" AND l.type = :type");
            params.addValue("type", normalizeType(type));
        }
        // Soonest to expire first: that is the order the owner has to act in.
        sql.append(" ORDER BY l.expiry_date ASC, l.id ASC");
        return db.jdbc().query(sql.toString(), params, (rs, i) -> map(rs));
    }

    @Transactional(readOnly = true)
    public LicenceDtos.LicenceResponse get(Long shopId, Long id) {
        return db.jdbc().query(SELECT + " AND l.id = :id", new MapSqlParameterSource("shop", shopId).addValue("id", id),
                (rs, i) -> map(rs)).stream().findFirst().orElseThrow(() -> new DomainException(404, "Licence not found"));
    }

    @Transactional
    public LicenceDtos.LicenceResponse update(Long shopId, Long id, LicenceDtos.LicenceUpdateRequest request) {
        get(shopId, id);
        String type = normalizeType(request.type());
        validateDates(request.issueDate(), request.expiryDate());
        try {
            db.jdbc().update("""
                    UPDATE licences SET licence_number = :number, type = :type, issuer = :issuer,
                           issue_date = :issue, expiry_date = :expiry, fssai_gstin = :gstin, notes = :notes,
                           status = :status, updated_at = :now
                    WHERE id = :id AND shop_id = :shop AND deleted_at IS NULL""", new MapSqlParameterSource()
                    .addValue("number", request.licenceNumber().trim())
                    .addValue("type", type)
                    .addValue("issuer", blank(request.issuer()))
                    .addValue("issue", Date.valueOf(request.issueDate()))
                    .addValue("expiry", Date.valueOf(request.expiryDate()))
                    .addValue("gstin", blank(request.fssaiGstin()))
                    .addValue("notes", blank(request.notes()))
                    .addValue("status", statusFor(request.expiryDate(), DateDefaults.today()).name())
                    .addValue("now", Timestamp.valueOf(LocalDateTime.now()))
                    .addValue("id", id).addValue("shop", shopId));
        } catch (RuntimeException e) {
            throw rethrowDuplicate(e, request.licenceNumber());
        }
        return get(shopId, id);
    }

    /**
     * The unique index is on the generated {@code live_number} column, so a clash
     * surfaces as a driver-level constraint violation rather than anything the
     * service asked for. Left alone it would reach the client as a 500, which reads
     * as a server fault instead of "that number is already registered".
     */
    private static RuntimeException rethrowDuplicate(RuntimeException e, String number) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof DataIntegrityViolationException) {
                return new DomainException(409,
                        "Licence number " + number + " is already registered");
            }
            if (t.getCause() == t) {
                break;
            }
        }
        return e;
    }

    @Transactional
    public LicenceDtos.LicenceResponse attachDocument(Long shopId, Long id, MultipartFile file) {
        get(shopId, id);
        FileStorageService.Stored stored = storage.store(shopId, UPLOAD_FOLDER, file, false);
        String previous = db.jdbc().queryForList(
                "SELECT document_path FROM licences WHERE id = :id AND shop_id = :shop AND deleted_at IS NULL "
                        + "AND document_path IS NOT NULL",
                Map.of("id", id, "shop", shopId), String.class).stream().findFirst().orElse(null);
        try {
            db.jdbc().update("""
                    UPDATE licences SET document_path = :path, document_name = :name, document_type = :type,
                           updated_at = :now
                    WHERE id = :id AND shop_id = :shop AND deleted_at IS NULL""", new MapSqlParameterSource()
                    .addValue("path", stored.path().toString())
                    .addValue("name", stored.originalName())
                    .addValue("type", stored.contentType())
                    .addValue("now", Timestamp.valueOf(LocalDateTime.now()))
                    .addValue("id", id).addValue("shop", shopId));
        } catch (RuntimeException e) {
            storage.delete(stored.path());
            throw e;
        }
        // Only drop the old file once the row points at the new one.
        if (previous != null) {
            storage.delete(previous);
        }
        return get(shopId, id);
    }

    /** Soft delete: the licence disappears from the register but its row, and therefore its renewal history, survives. */
    @Transactional
    public void delete(Long shopId, Long id) {
        if (db.jdbc().update("""
                UPDATE licences SET deleted_at = :now, updated_at = :now
                WHERE id = :id AND shop_id = :shop AND deleted_at IS NULL""",
                Map.of("now", Timestamp.valueOf(LocalDateTime.now()), "id", id, "shop", shopId)) == 0) {
            throw new DomainException(404, "Licence not found");
        }
    }

    // ==================== renewals ====================

    /**
     * Appends a renewal and moves the licence forward in one transaction. The
     * previous number and expiry are copied onto the history row first, so the
     * superseded document is never lost.
     */
    @Transactional
    public LicenceDtos.LicenceResponse renew(Long shopId, Long userId, Long id, LicenceDtos.RenewalRequest request,
                                              MultipartFile file) {
        validateDates(request.issueDate(), request.expiryDate());
        LicenceDtos.LicenceResponse before = get(shopId, id);

        FileStorageService.Stored stored = null;
        if (file != null && !file.isEmpty()) {
            stored = storage.store(shopId, UPLOAD_FOLDER, file, false);
        }
        LocalDateTime now = LocalDateTime.now();
        // A renewal that reissues under a new number replaces it; otherwise the
        // licence keeps the number it always had.
        String newNumber = blank(request.newNumber());
        try {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("shop_id", shopId);
            row.put("licence_id", id);
            row.put("previous_number", before.licenceNumber());
            row.put("previous_expiry_date", Date.valueOf(before.expiryDate()));
            row.put("new_number", newNumber);
            row.put("issue_date", Date.valueOf(request.issueDate()));
            row.put("expiry_date", Date.valueOf(request.expiryDate()));
            row.put("notes", blank(request.notes()));
            row.put("renewed_by", userId);
            row.put("created_at", Timestamp.valueOf(now));
            if (stored != null) {
                row.put("document_path", stored.path().toString());
                row.put("document_name", stored.originalName());
                row.put("document_type", stored.contentType());
            }
            db.insert("licence_renewals", row);

            // The bill identifier usually *is* the licence number (FSSAI/GST reissue on
            // renewal). When it was tracking the old number, carry the new one across so
            // bills stop printing a superseded registration; leave a deliberately
            // different identifier alone.
            String effectiveNumber = newNumber == null ? before.licenceNumber() : newNumber;
            boolean tracksNumber = before.fssaiGstin() != null
                    && before.fssaiGstin().equals(before.licenceNumber());
            String identifier = newNumber != null && tracksNumber ? newNumber : before.fssaiGstin();

            db.jdbc().update("""
                    UPDATE licences SET licence_number = :number, issue_date = :issue, expiry_date = :expiry,
                           fssai_gstin = COALESCE(:gstin, fssai_gstin), status = :status, updated_at = :now
                    WHERE id = :id AND shop_id = :shop AND deleted_at IS NULL""", new MapSqlParameterSource()
                    .addValue("number", effectiveNumber)
                    .addValue("gstin", identifier)
                    .addValue("issue", Date.valueOf(request.issueDate()))
                    .addValue("expiry", Date.valueOf(request.expiryDate()))
                    .addValue("status", statusFor(request.expiryDate(), DateDefaults.today()).name())
                    .addValue("now", Timestamp.valueOf(now))
                    .addValue("id", id).addValue("shop", shopId));

            // A renewal resets the reminder ladder: the next sweep may fire 60/30/7 again.
            db.jdbc().update("DELETE FROM licence_reminders WHERE licence_id = :id", Map.of("id", id));
        } catch (RuntimeException e) {
            if (stored != null) {
                storage.delete(stored.path());
            }
            throw newNumber == null ? e : rethrowDuplicate(e, newNumber);
        }
        return get(shopId, id);
    }

    private static final String RENEWAL_SELECT = """
            SELECT r.id, r.licence_id, r.previous_number, r.previous_expiry_date, r.new_number, r.issue_date,
                   r.expiry_date, r.document_path, r.document_name, r.document_type, r.notes, r.created_at,
                   u.name AS renewed_by
            FROM licence_renewals r LEFT JOIN users u ON u.id = r.renewed_by
            WHERE r.licence_id = :licence""";

    @Transactional(readOnly = true)
    public List<LicenceDtos.RenewalResponse> renewals(Long shopId, Long id) {
        get(shopId, id);
        return db.jdbc().query(RENEWAL_SELECT + " ORDER BY r.created_at DESC, r.id DESC",
                Map.of("licence", id), (rs, i) -> mapRenewal(rs));
    }

    @Transactional(readOnly = true)
    public LicenceDtos.RenewalResponse renewal(Long shopId, Long id, Long renewalId) {
        get(shopId, id);
        return db.jdbc().query(RENEWAL_SELECT + " AND r.id = :rid", Map.of("licence", id, "rid", renewalId),
                (rs, i) -> mapRenewal(rs)).stream().findFirst()
                .orElseThrow(() -> new DomainException(404, "Renewal not found"));
    }

    // ==================== documents ====================

    /** Tenant-checked read of the licence's own document. */
    @Transactional(readOnly = true)
    public Document document(Long shopId, Long id) {
        return readDocument("licences", shopId, id, "document_path", "document_name", "document_type");
    }

    @Transactional(readOnly = true)
    public Document renewalDocument(Long shopId, Long id, Long renewalId) {
        get(shopId, id);
        return readDocument("licence_renewals", shopId, renewalId, "document_path", "document_name", "document_type");
    }

    private Document readDocument(String table, Long shopId, Long rowId, String pathCol, String nameCol, String typeCol) {
        List<Map<String, Object>> rows = db.jdbc().queryForList(
                "SELECT " + pathCol + " AS file_path, " + nameCol + " AS file_name, " + typeCol + " AS file_type"
                        + " FROM " + table + " WHERE id = :id AND shop_id = :shop",
                Map.of("id", rowId, "shop", shopId));
        if (rows.isEmpty() || rows.get(0).get("file_path") == null) {
            throw new DomainException(404, "No document attached");
        }
        String storedPath = (String) rows.get(0).get("file_path");
        FileStorageService.Download download =
                storage.read(storedPath, (String) rows.get(0).get("file_name"), (String) rows.get(0).get("file_type"));
        return new Document(download.resource(), download.fileName(), download.contentType());
    }

    // ==================== summary ====================

    @Transactional(readOnly = true)
    public LicenceDtos.LicenceSummaryResponse summary(Long shopId) {
        LocalDate today = DateDefaults.today();
        List<LicenceDtos.LicenceResponse> all = list(shopId, null, null);
        long active = all.stream().filter(l -> "ACTIVE".equals(l.status())).count();
        long expiringSoon = all.stream().filter(l -> "EXPIRING_SOON".equals(l.status())).count();
        long expired = all.stream().filter(l -> "EXPIRED".equals(l.status())).count();

        // A required licence only counts as held while it is still live *and* carries the
        // number that gets printed on the bill; an expired or numberless row is a gap.
        List<String> missing = new ArrayList<>();
        for (String required : List.of("FSSAI", "GST")) {
            boolean held = all.stream().anyMatch(l -> required.equals(l.type())
                    && !"EXPIRED".equals(l.status()) && blank(l.fssaiGstin()) != null);
            if (!held) {
                missing.add(required);
            }
        }

        BillIdentifiers ids = billIdentifiers(shopId);
        LocalDate nextExpiry = all.stream()
                .filter(l -> !"EXPIRED".equals(l.status()))
                .map(LicenceDtos.LicenceResponse::expiryDate)
                .min(LocalDate::compareTo)
                .orElse(null);

        return new LicenceDtos.LicenceSummaryResponse(all.size(), active, expiringSoon, expired,
                within(all, 7), within(all, 30), within(all, 60), nextExpiry, ids.fssaiNumber(), ids.gstin(), missing);
    }

    private static long within(List<LicenceDtos.LicenceResponse> all, int days) {
        return all.stream().filter(l -> l.daysLeft() >= 0 && l.daysLeft() <= days).count();
    }

    /**
     * FSSAI number and GSTIN for the bill footer, taken from licences that have
     * not expired. Expired registrations must not be printed on a customer bill.
     */
    @Transactional(readOnly = true)
    public BillIdentifiers billIdentifiers(Long shopId) {
        return new BillIdentifiers(
                billIdentifier(shopId, "FSSAI"),
                billIdentifier(shopId, "GST"));
    }

    private String billIdentifier(Long shopId, String type) {
        return db.jdbc().queryForList("""
                SELECT COALESCE(NULLIF(l.fssai_gstin, ''), l.licence_number) AS identifier
                FROM licences l
                WHERE l.shop_id = :shop AND l.type = :type AND l.deleted_at IS NULL
                  AND l.expiry_date >= :today AND COALESCE(NULLIF(l.fssai_gstin, ''), l.licence_number) IS NOT NULL
                ORDER BY l.expiry_date ASC LIMIT 1""",
                new MapSqlParameterSource("shop", shopId).addValue("type", type).addValue("today", Date.valueOf(DateDefaults.today())),
                String.class).stream().findFirst().orElse(null);
    }

    // ==================== daily sweep ====================

    /**
     * Recomputes {@code status} for every live licence. Only rows whose status
     * actually moved are written, so {@code updated_at} stays meaningful.
     *
     * @return how many rows changed
     */
    @Transactional
    public int refreshStatuses(LocalDate today) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("today", Date.valueOf(today))
                .addValue("soon", Date.valueOf(today.plusDays(EXPIRING_SOON_DAYS)))
                .addValue("now", Timestamp.valueOf(LocalDateTime.now()));
        String computed = statusExpression("expiry_date");
        return db.jdbc().update("UPDATE licences SET status = " + computed + ", updated_at = :now"
                + " WHERE deleted_at IS NULL AND status <> " + computed, params);
    }

    /** A licence sitting exactly on a reminder threshold that has not been notified yet. */
    public record Reminder(Long licenceId, Long shopId, String licenceNumber, String type, String issuer,
                           LocalDate expiryDate, long daysLeft) {
    }

    /**
     * Licences due in 60, 30 or 7 days whose threshold has not already been
     * recorded in {@code licence_reminders}.
     */
    @Transactional(readOnly = true)
    public List<Reminder> dueReminders(LocalDate today) {
        return db.jdbc().query("""
                SELECT l.id, l.shop_id, l.licence_number, l.type, l.issuer, l.expiry_date,
                       DATEDIFF(l.expiry_date, :today) AS days_left
                FROM licences l
                WHERE l.deleted_at IS NULL
                  AND DATEDIFF(l.expiry_date, :today) IN (60, 30, 7)
                  AND NOT EXISTS (SELECT 1 FROM licence_reminders r
                                  WHERE r.licence_id = l.id
                                    AND r.days_left = DATEDIFF(l.expiry_date, :today))
                ORDER BY days_left DESC, l.expiry_date ASC""",
                Map.of("today", Date.valueOf(today)), (rs, i) -> new Reminder(rs.getLong("id"), rs.getLong("shop_id"),
                        rs.getString("licence_number"), rs.getString("type"), rs.getString("issuer"),
                        JdbcSupport.date(rs, "expiry_date"), rs.getLong("days_left")));
    }

    /**
     * Records a reminder as sent. The unique key on {@code (licence_id, days_left)}
     * makes this idempotent, so two sweeps racing each other cannot double-notify.
     *
     * @return false when this threshold had already been recorded
     */
    @Transactional
    public boolean recordReminder(Reminder reminder, String channel) {
        try {
            db.insert("licence_reminders", Map.of(
                    "shop_id", reminder.shopId(),
                    "licence_id", reminder.licenceId(),
                    "days_left", (int) reminder.daysLeft(),
                    "channel", channel,
                    "sent_at", Timestamp.valueOf(LocalDateTime.now())));
            return true;
        } catch (RuntimeException e) {
            log.debug("Reminder for licence {} at {}d already recorded", reminder.licenceId(), reminder.daysLeft());
            return false;
        }
    }

    // ==================== helpers ====================

    private static void fill(Map<String, Object> row, String type, String number, String issuer, LocalDate issue,
                             LocalDate expiry, String fssaiGstin, String notes) {
        if (blank(number) == null) {
            throw new DomainException(400, "Licence number is required");
        }
        row.put("licence_number", number.trim());
        row.put("type", type);
        row.put("issuer", blank(issuer));
        row.put("issue_date", Date.valueOf(issue));
        row.put("expiry_date", Date.valueOf(expiry));
        row.put("fssai_gstin", blank(fssaiGstin));
        row.put("notes", blank(notes));
    }

    private static void validateDates(LocalDate issue, LocalDate expiry) {
        if (expiry == null || issue == null) {
            throw new DomainException(400, "Both issue and expiry dates are required");
        }
        if (expiry.isBefore(issue)) {
            throw new DomainException(400, "Expiry date must not be before the issue date");
        }
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    static String normalizeType(String type) {
        String t = type == null ? "" : type.trim().toUpperCase(Locale.ROOT);
        if (!TYPES.contains(t)) {
            throw new DomainException(400, "Licence type must be one of " + TYPES);
        }
        return t;
    }

    private static String normalizeStatus(String status) {
        String s = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("ACTIVE", "EXPIRING_SOON", "EXPIRED").contains(s)) {
            throw new DomainException(400, "Status must be ACTIVE, EXPIRING_SOON or EXPIRED");
        }
        return s;
    }

    private static LicenceDtos.LicenceResponse map(ResultSet rs) throws SQLException {
        LocalDate expiry = JdbcSupport.date(rs, "expiry_date");
        LocalDateTime lastRenewed = JdbcSupport.dateTime(rs, "last_renewed_at");
        return new LicenceDtos.LicenceResponse(rs.getLong("id"), rs.getString("licence_number"), rs.getString("type"),
                rs.getString("issuer"), JdbcSupport.date(rs, "issue_date"), expiry,
                ChronoUnit.DAYS.between(DateDefaults.today(), expiry), rs.getString("status"), rs.getString("fssai_gstin"),
                rs.getString("document_path") != null, rs.getString("document_name"), rs.getString("document_type"),
                rs.getString("notes"), rs.getLong("renewal_count"),
                lastRenewed == null ? null : lastRenewed.toLocalDate(),
                JdbcSupport.iso(JdbcSupport.dateTime(rs, "created_at")),
                JdbcSupport.iso(JdbcSupport.dateTime(rs, "updated_at")));
    }

    private static LicenceDtos.RenewalResponse mapRenewal(ResultSet rs) throws SQLException {
        return new LicenceDtos.RenewalResponse(rs.getLong("id"), rs.getLong("licence_id"), rs.getString("previous_number"),
                JdbcSupport.date(rs, "previous_expiry_date"), rs.getString("new_number"), JdbcSupport.date(rs, "issue_date"),
                JdbcSupport.date(rs, "expiry_date"), rs.getString("document_path") != null, rs.getString("document_name"),
                rs.getString("notes"), rs.getString("renewed_by"), JdbcSupport.iso(JdbcSupport.dateTime(rs, "created_at")));
    }
}