package gr.barber.booking.notification;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Checks for due reminders every few minutes. Off in tests (app.reminders.enabled=false). */
@Component
@EnableScheduling
@ConditionalOnProperty(value = "app.reminders.enabled", havingValue = "true", matchIfMissing = true)
public class ReminderScheduler {

    private final AppointmentReminderService reminderService;
    private final PendingRequestService pendingRequestService;

    public ReminderScheduler(AppointmentReminderService reminderService, PendingRequestService pendingRequestService) {
        this.reminderService = reminderService;
        this.pendingRequestService = pendingRequestService;
    }

    @Scheduled(fixedDelayString = "${app.reminders.interval:PT5M}", initialDelayString = "PT1M")
    public void sendDueReminders() {
        pendingRequestService.process();
        reminderService.sendDueReminders();
    }
}
