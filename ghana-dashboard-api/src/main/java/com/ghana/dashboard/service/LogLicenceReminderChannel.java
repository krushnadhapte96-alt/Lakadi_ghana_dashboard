package com.ghana.dashboard.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Default channel: reminders land in the application log. This is what runs when
 * SMTP is not configured, and it keeps the reminder ledger meaningful so the
 * thresholds are not re-fired every day.
 */
@Component
@ConditionalOnProperty(prefix = "app.mail", name = "enabled", havingValue = "false", matchIfMissing = true)
public class LogLicenceReminderChannel implements LicenceReminderChannel {

    private static final Logger log = LoggerFactory.getLogger(LogLicenceReminderChannel.class);

    @Override
    public String channel() {
        return "LOG";
    }

    @Override
    public boolean send(LicenceService.Reminder reminder) {
        log.info("Licence reminder: {} ({}) {} expires in {} day(s) on {} for shop {}",
                reminder.licenceNumber(), reminder.type(), reminder.issuer(), reminder.daysLeft(),
                reminder.expiryDate(), reminder.shopId());
        return true;
    }
}