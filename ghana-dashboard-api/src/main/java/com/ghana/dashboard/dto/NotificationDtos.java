package com.ghana.dashboard.dto;

import java.time.LocalDateTime;
import java.util.List;

public final class NotificationDtos {

    private NotificationDtos() {
    }

    /**
     * The alert kinds the shop can be told about. The CHECK constraint on
     * {@code notifications.type} carries the same list, so a typo here fails the
     * insert loudly instead of quietly producing an unreachable notification.
     */
    public enum NotificationType {
        LOW_STOCK,
        NEW_ORDER,
        ORDER_OVERDUE,
        EXPIRY,
        LICENCE,
        UDHAAR_OVERDUE,
        SEED_LOW,
        SUPPLIER_DUE,
        REFILL_DUE,
        DAY_NOT_CLOSED
    }

    /** Drives the bell's accent colour and whether the sound plays. */
    public enum Severity {
        INFO,
        SUCCESS,
        WARNING,
        CRITICAL
    }

    public record NotificationResponse(
            Long id,
            String type,
            String severity,
            String title,
            String message,
            String link,
            LocalDateTime createdAt,
            boolean read
    ) {
    }

    public record ListResponse(
            List<NotificationResponse> items,
            long total,
            int page,
            int size,
            long unreadCount
    ) {
    }

    /** Badge payload. Separate from the list so polling stays a tiny response. */
    public record UnreadCountResponse(long count) {
    }

    public record MarkReadResponse(long updated) {
    }

    /** What a manually triggered sweep did, so the dev trigger is not a black box. */
    public record JobResult(String job, int created, int skipped) {
    }

    /**
     * @param userId null addresses every user of the shop
     */
    public record NewNotification(
            Long shopId,
            Long userId,
            NotificationType type,
            Severity severity,
            String title,
            String message,
            String link,
            String dedupeKey
    ) {
    }
}