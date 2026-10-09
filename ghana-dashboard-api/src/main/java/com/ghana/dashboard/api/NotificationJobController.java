package com.ghana.dashboard.api;

import com.ghana.dashboard.common.ApiResponse;
import com.ghana.dashboard.common.DomainException;
import com.ghana.dashboard.dto.NotificationDtos.JobResult;
import com.ghana.dashboard.dto.NotificationDtos.NewNotification;
import com.ghana.dashboard.dto.NotificationDtos.NotificationType;
import com.ghana.dashboard.dto.NotificationDtos.Severity;
import com.ghana.dashboard.security.CurrentUser;
import com.ghana.dashboard.service.NotificationJobs;
import com.ghana.dashboard.service.NotificationService;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;

/**
 * Dev-only trigger for the notification jobs.
 *
 * <p>Pinning each sweep to a fixed time makes it awkward to check that a job really produces the
 * right alert, so in development you can fire any of them on demand and see exactly what the bell
 * would have received. The endpoint is absent outside the dev profile: it can manufacture alert
 * noise, and it lets a session run a job at an hour when that job makes no sense.
 *
 * <p>Owner only, and always for the caller's own shop - a job writes notifications that other
 * people will see, so it is not something a staff account should be able to do.
 */
@RestController
@RequestMapping("/api/v1/dev/notifications")
@Profile("dev")
@PreAuthorize("hasRole('OWNER')")
public class NotificationJobController {

    private final NotificationJobs jobs;
    private final NotificationService notifications;

    public NotificationJobController(NotificationJobs jobs, NotificationService notifications) {
        this.jobs = jobs;
        this.notifications = notifications;
    }

    /** What can be triggered, so the endpoint is discoverable without reading the source. */
    @GetMapping("/jobs")
    public ApiResponse<List<String>> jobs() {
        return ApiResponse.ok(NotificationJobs.jobNames());
    }

    @PostMapping("/jobs/{job}")
    public ApiResponse<JobResult> run(@PathVariable String job) {
        return ApiResponse.ok("Job " + job + " finished", jobs.run(job, CurrentUser.getShopId()));
    }

    /** Every job at once, which is what the morning sweep does. */
    @PostMapping("/jobs")
    public ApiResponse<List<JobResult>> runAll() {
        return ApiResponse.ok("All jobs finished", jobs.runAll(CurrentUser.getShopId()));
    }

    /**
     * Creates one notification by hand, for checking how the bell renders a severity or plays the
     * new-order sound without having to arrange a real sale first. A unique de-dupe key keeps
     * repeated clicks from collapsing into one row.
     */
    @PostMapping("/test")
    public ApiResponse<DevResult> test(
            @RequestParam(defaultValue = "LOW_STOCK") String type,
            @RequestParam(defaultValue = "WARNING") String severity,
            @RequestParam(defaultValue = "Test notification") String title,
            @RequestParam(required = false) String message,
            @RequestParam(required = false) String link) {
        boolean written = notifications.create(new NewNotification(
                CurrentUser.getShopId(), null, parseType(type), parseSeverity(severity),
                title, message, link, "DEV_TEST:" + System.nanoTime()));
        return ApiResponse.ok(written ? "Notification created" : "Nothing to create",
                new DevResult(written, unread()));
    }

    /** What the bell would show right now. */
    @GetMapping("/unread-count")
    public ApiResponse<DevResult> unreadCount() {
        return ApiResponse.ok(new DevResult(false, unread()));
    }

    private long unread() {
        return notifications.unreadCount(CurrentUser.getShopId(), CurrentUser.getUserId());
    }

    private static NotificationType parseType(String raw) {
        try {
            return NotificationType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new DomainException(400, "Unknown notification type: " + raw);
        }
    }

    private static Severity parseSeverity(String raw) {
        try {
            return Severity.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new DomainException(400, "Unknown severity: " + raw);
        }
    }

    /** The dev endpoint's job is reporting, not modelling, so it gets its own tiny payload. */
    public record DevResult(boolean written, long unreadCount) {
    }
}