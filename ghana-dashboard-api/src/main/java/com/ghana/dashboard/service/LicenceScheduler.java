package com.ghana.dashboard.service;

import com.ghana.dashboard.common.DateDefaults;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * Daily sweep for the licence register: brings every stored status back in line
 * with its expiry date, then fires the 60/30/7-day reminders that have not gone
 * out yet. Reminders are recorded against {@code licence_reminders}, so re-running
 * the sweep never notifies twice for the same threshold.
 */
@Component
public class LicenceScheduler {

    private static final Logger log = LoggerFactory.getLogger(LicenceScheduler.class);

    private final LicenceService licences;
    private final List<LicenceReminderChannel> channels;

    public LicenceScheduler(LicenceService licences, List<LicenceReminderChannel> channels) {
        this.licences = licences;
        this.channels = channels;
    }

    @Scheduled(cron = "0 15 7 * * *", zone = "Asia/Kolkata")
    public void sweep() {
        LocalDate today = DateDefaults.today();
        int changed = licences.refreshStatuses(today);
        List<LicenceService.Reminder> due = licences.dueReminders(today);
        int sent = notify(due);
        log.info("Licence sweep date={} statusChanges={} remindersDue={} remindersSent={} channel={}",
                today, changed, due.size(), sent, channels.isEmpty() ? "none" : channels.get(0).channel());
    }

    /**
     * Delivers over every configured channel and records the reminder once, after
     * the first success. A channel that throws or reports failure is logged and
     * skipped so a broken relay cannot block the rest.
     */
    private int notify(List<LicenceService.Reminder> due) {
        int sent = 0;
        for (LicenceService.Reminder reminder : due) {
            boolean delivered = false;
            boolean recorded = false;
            for (LicenceReminderChannel channel : channels) {
                boolean accepted;
                try {
                    accepted = channel.send(reminder);
                } catch (RuntimeException e) {
                    log.warn("Licence reminder channel {} failed for licence {}", channel.channel(),
                            reminder.licenceId(), e);
                    continue;
                }
                if (!accepted) {
                    continue;
                }
                delivered = true;
                // Record against the first channel that accepted it; the unique key on
                // (licence_id, days_left) would reject the later ones anyway. A rejected
                // channel must stay unrecorded, otherwise the row is written as delivered
                // while the owner never saw it and the retry is skipped for good.
                if (!recorded) {
                    recorded = licences.recordReminder(reminder, channel.channel());
                }
            }
            if (delivered && recorded) {
                sent++;
            }
        }
        return sent;
    }
}