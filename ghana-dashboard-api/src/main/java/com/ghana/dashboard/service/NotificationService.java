package com.ghana.dashboard.service;

import com.ghana.dashboard.common.DomainException;
import com.ghana.dashboard.common.JdbcSupport;
import com.ghana.dashboard.dto.NotificationDtos;
import com.ghana.dashboard.dto.NotificationDtos.ListResponse;
import com.ghana.dashboard.dto.NotificationDtos.MarkReadResponse;
import com.ghana.dashboard.dto.NotificationDtos.NewNotification;
import com.ghana.dashboard.dto.NotificationDtos.NotificationResponse;
import com.ghana.dashboard.dto.NotificationDtos.NotificationType;
import com.ghana.dashboard.dto.NotificationDtos.Severity;
import com.ghana.dashboard.dto.NotificationDtos.UnreadCountResponse;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The notification inbox.
 *
 * <p>Two rules shape everything here:
 *
 * <ul>
 *   <li><b>De-duplication.</b> Sweeps run repeatedly (and the dev trigger lets you run them on
 *       demand), so an alert that is still true would otherwise pile a fresh copy into the bell
 *       every run. A {@code dedupeKey} names the thing being complained about - the product id, the
 *       order id, the licence - and an identical key is refused for {@value #DEDUPE_WINDOW_HOURS}
 *       hours. The window is a moving one rather than a UNIQUE index because an unresolved alert
 *       has to be allowed to nag again the next day.
 *   <li><b>Per-user read state.</b> A row addressed to one user records the read on the row
 *       itself. A broadcast has no single owner, so a shared {@code read_at} would let whichever
 *       user opened the bell first clear it for the whole shop; broadcasts record readers in
 *       {@code notification_reads} instead.
 * </ul>
 */
@Service
public class NotificationService {

    /** How long an identical {@code dedupeKey} stays quiet. */
    static final int DEDUPE_WINDOW_HOURS = 24;

    /** Open SSE subscriptions, keyed by shop, so a push only visits that shop's listeners. */
    private final Map<Long, List<SseEmitter>> streamsByShop = new ConcurrentHashMap<>();

    private final JdbcSupport db;

    public NotificationService(JdbcSupport db) {
        this.db = db;
    }

    // ==================== writing ====================

    /**
     * Records a notification unless the same {@code dedupeKey} already fired inside the window.
     *
     * @return true when a row was written
     */
    @Transactional
    public boolean create(NewNotification request) {
        if (request.dedupeKey() != null && seenRecently(request.shopId(), request.type(), request.dedupeKey())) {
            return false;
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("shop_id", request.shopId());
        row.put("user_id", request.userId());
        row.put("type", request.type().name());
        row.put("severity", request.severity().name());
        row.put("title", request.title());
        row.put("message", request.message());
        row.put("link", request.link());
        row.put("dedupe_key", request.dedupeKey());
        row.put("created_at", Timestamp.valueOf(LocalDateTime.now()));
        long id = db.insert("notifications", row);

        // Tell any open bell straight away; the frontend also polls, so a missed push is harmless.
        NotificationResponse saved = readOne(request.shopId(), id);
        if (saved != null) {
            pushToShop(request.shopId(), "notification", saved);
        }
        return true;
    }

    /** Creates the same notification for every active user of the shop, one row each. */
    @Transactional
    public int createForEachUser(Long shopId, NotificationType type, Severity severity, String title,
                                 String message, String link, String dedupeKeyPrefix) {
        int created = 0;
        List<Long> userIds = db.jdbc().queryForList(
                "SELECT id FROM users WHERE shop_id = :shop AND active = 1 AND deleted_at IS NULL",
                new MapSqlParameterSource("shop", shopId), Long.class);
        for (Long userId : userIds) {
            String key = dedupeKeyPrefix == null ? null : dedupeKeyPrefix + ":" + userId;
            if (create(new NewNotification(shopId, userId, type, severity, title, message, link, key))) {
                created++;
            }
        }
        return created;
    }

    private boolean seenRecently(Long shopId, NotificationType type, String dedupeKey) {
        LocalDateTime since = LocalDateTime.now().minusHours(DEDUPE_WINDOW_HOURS);
        Integer hits = db.jdbc().queryForObject("""
                SELECT COUNT(*) FROM notifications
                WHERE shop_id = :shop AND type = :type AND dedupe_key = :key AND created_at >= :since""",
                new MapSqlParameterSource()
                        .addValue("shop", shopId).addValue("type", type.name())
                        .addValue("key", dedupeKey).addValue("since", Timestamp.valueOf(since)),
                Integer.class);
        return hits != null && hits > 0;
    }

    // ==================== reading ====================

    @Transactional(readOnly = true)
    public ListResponse list(Long shopId, Long userId, String type, boolean unreadOnly, int page, int size) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("shop", shopId).addValue("userId", userId)
                .addValue("limit", size).addValue("offset", page * size);
        String filter = "";
        if (type != null && !type.isBlank()) {
            NotificationType parsed = parseType(type);
            params.addValue("type", parsed.name());
            filter += " AND n.type = :type";
        }
        if (unreadOnly) {
            filter += unreadClause();
        }

        long total = count(params, filter);
        long unread = unreadCount(shopId, userId);

        String sql = """
                SELECT n.id, n.type, n.severity, n.title, n.message, n.link, n.created_at,
                       n.user_id, COALESCE(n.read_at, nr.read_at) AS effective_read_at
                FROM notifications n
                LEFT JOIN notification_reads nr ON nr.notification_id = n.id AND nr.user_id = :userId
                WHERE (n.user_id IS NULL OR n.user_id = :userId) AND n.shop_id = :shop"""
                + filter + " ORDER BY n.created_at DESC, n.id DESC LIMIT :limit OFFSET :offset";

        List<NotificationResponse> items = db.jdbc().query(sql, params, (rs, i) -> new NotificationResponse(
                rs.getLong("id"),
                rs.getString("type"),
                rs.getString("severity"),
                rs.getString("title"),
                rs.getString("message"),
                rs.getString("link"),
                JdbcSupport.dateTime(rs, "created_at"),
                rs.getTimestamp("effective_read_at") != null));

        return new ListResponse(items, total, page, size, unread);
    }

    /**
     * The badge number: rows addressed to me or to the shop that I have not opened yet. A targeted
     * row counts until its own {@code read_at} is set; a broadcast counts until I have a row in
     * {@code notification_reads}.
     */
    @Transactional(readOnly = true)
    public long unreadCount(Long shopId, Long userId) {
        Long count = db.jdbc().queryForObject("""
                SELECT COUNT(*) FROM notifications n
                WHERE n.shop_id = :shop AND (n.user_id IS NULL OR n.user_id = :userId)
                  AND (CASE WHEN n.user_id IS NULL THEN NOT EXISTS (
                            SELECT 1 FROM notification_reads r
                            WHERE r.notification_id = n.id AND r.user_id = :userId)
                        ELSE n.read_at IS NULL END)""",
                new MapSqlParameterSource().addValue("shop", shopId).addValue("userId", userId),
                Long.class);
        return count == null ? 0 : count;
    }

    @Transactional(readOnly = true)
    public UnreadCountResponse unread(Long shopId, Long userId) {
        return new UnreadCountResponse(unreadCount(shopId, userId));
    }

    /**
     * Marks one notification read for the calling user. Targeted rows stamp themselves; broadcasts
     * get a ledger row so the read is only ours.
     */
    @Transactional
    public MarkReadResponse markRead(Long shopId, Long userId, Long notificationId) {
        NotificationResponse current = readOne(shopId, notificationId);
        if (current == null) {
            return new MarkReadResponse(0);
        }
        Integer targeted = db.jdbc().queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE id = :id AND shop_id = :shop AND user_id = :userId",
                new MapSqlParameterSource().addValue("id", notificationId)
                        .addValue("shop", shopId).addValue("userId", userId),
                Integer.class);
        if (targeted != null && targeted > 0) {
            db.jdbc().update("UPDATE notifications SET read_at = :now WHERE id = :id AND read_at IS NULL",
                    new MapSqlParameterSource().addValue("id", notificationId)
                            .addValue("now", Timestamp.valueOf(LocalDateTime.now())));
            return new MarkReadResponse(1);
        }
        return new MarkReadResponse(markBroadcastRead(shopId, userId, notificationId));
    }

    private int markBroadcastRead(Long shopId, Long userId, Long notificationId) {
        // Guarded on the shop and on the row being a broadcast, so a targeted notification cannot
        // be read through this path and a cross-shop id cannot be touched at all.
        int inserted = db.jdbc().update("""
                INSERT INTO notification_reads (notification_id, user_id, read_at)
                SELECT n.id, :userId, :now FROM notifications n
                WHERE n.id = :id AND n.shop_id = :shop AND n.user_id IS NULL
                ON DUPLICATE KEY UPDATE read_at = VALUES(read_at)""",
                new MapSqlParameterSource().addValue("id", notificationId)
                        .addValue("shop", shopId).addValue("userId", userId)
                        .addValue("now", Timestamp.valueOf(LocalDateTime.now())));
        return inserted > 0 ? 1 : 0;
    }

    /** Clears the whole bell for the calling user, both targeted rows and broadcasts. */
    @Transactional
    public MarkReadResponse markAllRead(Long shopId, Long userId) {
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("shop", shopId).addValue("userId", userId)
                .addValue("now", Timestamp.valueOf(LocalDateTime.now()));
        int targeted = db.jdbc().update("""
                UPDATE notifications SET read_at = :now
                WHERE shop_id = :shop AND user_id = :userId AND read_at IS NULL""", p);
        int broadcast = db.jdbc().update("""
                INSERT INTO notification_reads (notification_id, user_id, read_at)
                SELECT n.id, :userId, :now FROM notifications n
                WHERE n.shop_id = :shop AND n.user_id IS NULL
                  AND NOT EXISTS (SELECT 1 FROM notification_reads r
                                  WHERE r.notification_id = n.id AND r.user_id = :userId)
                ON DUPLICATE KEY UPDATE read_at = VALUES(read_at)""", p);
        return new MarkReadResponse(targeted + Math.max(broadcast, 0));
    }

    // ==================== server-sent events ====================

    public SseEmitter subscribe(Long shopId, Long userId) {
        // 30 minutes: long enough for a shift, short enough that a laptop waking from sleep gets a
        // fresh stream instead of writing into a dead socket.
        SseEmitter emitter = new SseEmitter(Duration.ofMinutes(30).toMillis());
        streamsByShop.computeIfAbsent(shopId, key -> new CopyOnWriteArrayList<>()).add(emitter);
        Runnable remove = () -> {
            List<SseEmitter> list = streamsByShop.get(shopId);
            if (list != null) {
                list.remove(emitter);
            }
        };
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(error -> remove.run());
        try {
            emitter.send(SseEmitter.event().name("connected")
                    .data(Map.of("unreadCount", unreadCount(shopId, userId))));
        } catch (Exception e) {
            remove.run();
        }
        return emitter;
    }

    private void pushToShop(Long shopId, String event, NotificationResponse payload) {
        List<SseEmitter> emitters = streamsByShop.get(shopId);
        if (emitters == null || emitters.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name(event).data(payload));
            } catch (Exception e) {
                // A dead subscription must never fail the write that triggered it.
                emitters.remove(emitter);
            }
        }
    }

    // ==================== helpers ====================

    private NotificationResponse readOne(Long shopId, Long id) {
        List<NotificationResponse> rows = db.jdbc().query("""
                SELECT n.id, n.type, n.severity, n.title, n.message, n.link, n.created_at, n.user_id
                FROM notifications n WHERE n.shop_id = :shop AND n.id = :id""",
                new MapSqlParameterSource().addValue("shop", shopId).addValue("id", id),
                (rs, i) -> new NotificationResponse(
                        rs.getLong("id"), rs.getString("type"), rs.getString("severity"),
                        rs.getString("title"), rs.getString("message"), rs.getString("link"),
                        JdbcSupport.dateTime(rs, "created_at"), false));
        return rows.isEmpty() ? null : rows.get(0);
    }

    private long count(MapSqlParameterSource params, String filter) {
        String sql = """
                SELECT COUNT(*) FROM notifications n
                WHERE (n.user_id IS NULL OR n.user_id = :userId) AND n.shop_id = :shop"""
                + filter;
        Long value = db.jdbc().queryForObject(sql, params, Long.class);
        return value == null ? 0 : value;
    }

    /**
     * Shared by the count and the list so the total, the page and the badge can never disagree
     * about what "unread" means.
     *
     * <p>Assembled from plain strings on purpose: as a text block this would lose the leading space
     * that keeps it separable from whatever clause it is appended to.
     */
    private String unreadClause() {
        return " AND (CASE WHEN n.user_id IS NULL THEN NOT EXISTS ("
                + "SELECT 1 FROM notification_reads r "
                + "WHERE r.notification_id = n.id AND r.user_id = :userId) "
                + "ELSE n.read_at IS NULL END)";
    }

    /** Every stored row the caller can see, newest first. Used by the dev trigger's own reporting. */
    @Transactional(readOnly = true)
    public List<String> typesPresent(Long shopId) {
        List<String> types = new ArrayList<>();
        db.jdbc().query("SELECT DISTINCT type FROM notifications WHERE shop_id = :shop ORDER BY type",
                new MapSqlParameterSource("shop", shopId), rs -> {
                    types.add(rs.getString("type"));
                });
        return types;
    }

    private static NotificationType parseType(String raw) {
        try {
            return NotificationType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new DomainException(400, "Unknown notification type: " + raw);
        }
    }
}