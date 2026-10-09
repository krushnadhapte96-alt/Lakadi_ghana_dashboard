package com.ghana.dashboard.api;

import com.ghana.dashboard.common.ApiResponse;
import com.ghana.dashboard.dto.NotificationDtos;
import com.ghana.dashboard.security.CurrentUser;
import com.ghana.dashboard.service.NotificationService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * The bell's HTTP surface. Every call is scoped to the caller's own shop and user id, so no
 * endpoint here can be used to read somebody else's inbox.
 */
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notifications;

    public NotificationController(NotificationService notifications) {
        this.notifications = notifications;
    }

    @GetMapping
    public ApiResponse<NotificationDtos.ListResponse> list(
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(notifications.list(CurrentUser.getShopId(), CurrentUser.getUserId(),
                type, unreadOnly, page, Math.min(size, 100)));
    }

    /** Kept separate from the list so the bell can poll a two-number response. */
    @GetMapping("/unread-count")
    public ApiResponse<NotificationDtos.UnreadCountResponse> unreadCount() {
        return ApiResponse.ok(notifications.unread(CurrentUser.getShopId(), CurrentUser.getUserId()));
    }

    @PostMapping("/{id}/read")
    public ApiResponse<NotificationDtos.MarkReadResponse> markRead(@PathVariable Long id) {
        return ApiResponse.ok("Notification marked read",
                notifications.markRead(CurrentUser.getShopId(), CurrentUser.getUserId(), id));
    }

    @PostMapping("/read-all")
    public ApiResponse<NotificationDtos.MarkReadResponse> markAllRead() {
        return ApiResponse.ok("All notifications marked read",
                notifications.markAllRead(CurrentUser.getShopId(), CurrentUser.getUserId()));
    }

    /**
     * Live push. The frontend subscribes with EventSource and falls back to polling
     * {@code /unread-count}, so a browser or proxy that cannot hold the stream open still gets
     * fresh badges.
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        return notifications.subscribe(CurrentUser.getShopId(), CurrentUser.getUserId());
    }
}