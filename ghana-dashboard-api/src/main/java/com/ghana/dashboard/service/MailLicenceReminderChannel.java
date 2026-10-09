package com.ghana.dashboard.service;

import com.ghana.dashboard.config.AppProperties;
import com.ghana.dashboard.common.JdbcSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Optional SMTP delivery of licence reminders. Only instantiated when
 * {@code app.mail.enabled=true}, so a deployment without mail configured never
 * touches {@link JavaMailSender}.
 */
@Component
@ConditionalOnProperty(prefix = "app.mail", name = "enabled", havingValue = "true")
public class MailLicenceReminderChannel implements LicenceReminderChannel {

    private static final Logger log = LoggerFactory.getLogger(MailLicenceReminderChannel.class);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy");

    private final JavaMailSender mailSender;
    private final AppProperties props;
    private final JdbcSupport db;

    public MailLicenceReminderChannel(JavaMailSender mailSender, AppProperties props, JdbcSupport db) {
        this.mailSender = mailSender;
        this.props = props;
        this.db = db;
    }

    @Override
    public String channel() {
        return "MAIL";
    }

    @Override
    public boolean send(LicenceService.Reminder reminder) {
        String to = recipient(reminder.shopId());
        if (to == null) {
            log.warn("Licence {} needs a reminder but shop {} has no owner email; skipping mail",
                    reminder.licenceNumber(), reminder.shopId());
            return false;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(props.getMail().getFrom());
        message.setTo(to);
        message.setSubject(String.format("Licence %s expires in %d day(s)", reminder.licenceNumber(), reminder.daysLeft()));
        message.setText(String.format("""
                Your %s licence is due for renewal.

                  Licence: %s
                  Issued by: %s
                  Expiry date: %s
                  Days remaining: %d

                  Renew it from the Licences screen in the dashboard.
                """, reminder.type(), reminder.licenceNumber(),
                reminder.issuer() == null ? "-" : reminder.issuer(),
                reminder.expiryDate().format(DATE), reminder.daysLeft()));
        try {
            mailSender.send(message);
            return true;
        } catch (RuntimeException e) {
            // Leave the reminder unrecorded so tomorrow's sweep retries.
            log.warn("Could not mail licence reminder for shop {}", reminder.shopId(), e);
            return false;
        }
    }

    /** The shop's active owner, unless {@code app.mail.recipient-override} is set. */
    private String recipient(Long shopId) {
        String override = props.getMail().getRecipientOverride();
        if (override != null && !override.isBlank()) {
            return override.trim();
        }
        return db.jdbc().queryForList("""
                SELECT email FROM users
                WHERE shop_id = :shop AND role = 'OWNER' AND active = TRUE AND deleted_at IS NULL
                  AND email IS NOT NULL AND email <> ''
                ORDER BY id LIMIT 1""", Map.of("shop", shopId), String.class).stream().findFirst().orElse(null);
    }
}