package com.ghana.dashboard.service;

/**
 * Where a licence reminder is delivered. One bean per available channel: the log
 * channel is always present, the mail channel only when {@code app.mail.enabled=true}.
 * The in-app notification centre (a later prompt) can join as a third implementation.
 */
public interface LicenceReminderChannel {

    /** Persisted in {@code licence_reminders.channel}; must match the table's CHECK constraint. */
    String channel();

    /**
     * @return false when delivery failed, so the caller leaves the reminder unrecorded
     *         and the next sweep tries again
     */
    boolean send(LicenceService.Reminder reminder);
}