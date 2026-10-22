package gr.barber.booking.notification;

import gr.barber.booking.auth.UnverifiedAccountCleanup;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Every few minutes: closes unanswered requests, sends due reminders and removes unverified accounts.
 * Off in tests (app.reminders.enabled=false).
 */
@Component
@EnableScheduling
@ConditionalOnProperty(value = "app.reminders.enabled", havingValue = "true", matchIfMissing = true)
public class ReminderScheduler {

    private final AppointmentReminderService reminderService;
    private final PendingRequestService pendingRequestService;
    private final UnverifiedAccountCleanup unverifiedAccountCleanup;

    public ReminderScheduler(AppointmentReminderService reminderService,
                             PendingRequestService pendingRequestService,
                             UnverifiedAccountCleanup unverifiedAccountCleanup) {
        this.reminderService = reminderService;
        this.pendingRequestService = pendingRequestService;
        this.unverifiedAccountCleanup = unverifiedAccountCleanup;
    }

    @Scheduled(fixedDelayString = "${app.reminders.interval:PT5M}", initialDelayString = "PT1M")
    public void sendDueReminders() {
        pendingRequestService.process();
        reminderService.sendDueReminders();
        unverifiedAccountCleanup.process();
    }
}
