package com.ghana.dashboard.service;

import com.ghana.dashboard.domain.Shop;
import com.ghana.dashboard.dto.NotificationDtos.JobResult;
import com.ghana.dashboard.repository.ShopRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * Drives {@link NotificationJobs} on a schedule.
 *
 * <p>Everything except the closing check runs once in the morning: most of these facts - expiry,
 * licence dates, overdue balances - do not change during a trading day, and a shop that sells one
 * pouch of milk at 11:00 does not deserve nine fresh notifications because of it. The two alerts
 * that must react to live activity (low stock and new orders) are driven by events instead; the
 * morning run is their safety net.
 *
 * <p>The day-not-closed check has its own slot because its whole purpose is the time of day.
 */
@Component
public class NotificationScheduler {

    private static final Logger log = LoggerFactory.getLogger(NotificationScheduler.class);

    private final ShopRepository shopRepository;
    private final NotificationJobs jobs;

    public NotificationScheduler(ShopRepository shopRepository, NotificationJobs jobs) {
        this.shopRepository = shopRepository;
        this.jobs = jobs;
    }

    /** 07:30, after the expiry and licence sweeps have refreshed their status columns. */
    @Scheduled(cron = "0 30 7 * * *", zone = "Asia/Kolkata")
    public void morningSweep() {
        LocalDate today = LocalDate.now();
        for (Shop shop : shopRepository.findAll()) {
            try {
                List<JobResult> results = jobs.runAll(shop.getId());
                int created = results.stream().mapToInt(JobResult::created).sum();
                if (created > 0) {
                    log.info("Notification sweep shop={} date={} created={} detail={}",
                            shop.getId(), today, created, results);
                }
            } catch (RuntimeException e) {
                // One shop's bad data must not stop every other shop's notifications.
                log.error("Notification sweep failed for shop={}", shop.getId(), e);
            }
        }
    }

    /**
     * 22:00 IST - end of the trading day for a milk and provisions shop. If the cash book is still
     * open by now, someone needs to count the till and close it.
     */
    @Scheduled(cron = "0 0 22 * * *", zone = "Asia/Kolkata")
    public void dayNotClosedSweep() {
        for (Shop shop : shopRepository.findAll()) {
            try {
                JobResult result = jobs.dayNotClosed(shop.getId());
                if (result.created() > 0) {
                    log.info("Day-not-closed notification shop={} {}", shop.getId(), result);
                }
            } catch (RuntimeException e) {
                log.error("Day-not-closed sweep failed for shop={}", shop.getId(), e);
            }
        }
    }
}