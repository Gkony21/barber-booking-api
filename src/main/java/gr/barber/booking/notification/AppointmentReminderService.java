package gr.barber.booking.notification;

import gr.barber.booking.config.BookingProperties;
import gr.barber.booking.model.Appointment;
import gr.barber.booking.repository.AppointmentRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Emails customers before confirmed appointments: once the day before (up to 24 h ahead) and
 * once about 2 hours before. A reminder is skipped when the booking itself was made inside that
 * window, since the confirmation email has just arrived.
 */
@Service
public class AppointmentReminderService {

    static final Duration DAY_AHEAD = Duration.ofHours(24);
    static final Duration SOON_AHEAD = Duration.ofHours(2);

    private final AppointmentRepository appointmentRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate transactions;
    private final BookingProperties bookingProperties;
    private final Clock clock;

    public AppointmentReminderService(AppointmentRepository appointmentRepository,
                                      ApplicationEventPublisher eventPublisher,
                                      TransactionTemplate transactions,
                                      BookingProperties bookingProperties,
                                      Clock clock) {
        this.appointmentRepository = appointmentRepository;
        this.eventPublisher = eventPublisher;
        this.transactions = transactions;
        this.bookingProperties = bookingProperties;
        this.clock = clock;
    }

    /** Sends every reminder that is due now; safe to call repeatedly. */
    public void sendDueReminders() {
        Instant now = clock.instant();

        // Each appointment in its own transaction: the email is sent after that commit.
        for (Appointment candidate : appointmentRepository.findConfirmedStartingBetween(now.plus(SOON_AHEAD), now.plus(DAY_AHEAD))) {
            if (candidate.getCreatedAt().isBefore(candidate.getStartAt().minus(DAY_AHEAD))) {
                remind(candidate.getId(), AppointmentEventType.REMINDER_DAY, now);
            }
        }
        for (Appointment candidate : appointmentRepository.findConfirmedStartingBetween(now, now.plus(SOON_AHEAD))) {
            if (candidate.getCreatedAt().isBefore(candidate.getStartAt().minus(SOON_AHEAD))) {
                remind(candidate.getId(), AppointmentEventType.REMINDER_SOON, now);
            }
        }
    }

    private void remind(Long appointmentId, AppointmentEventType type, Instant now) {
        transactions.executeWithoutResult(status -> {
            int claimed = type == AppointmentEventType.REMINDER_DAY
                    ? appointmentRepository.claimDayReminder(appointmentId, now)
                    : appointmentRepository.claimSoonReminder(appointmentId, now);
            if (claimed == 1) {
                appointmentRepository.findById(appointmentId).ifPresent(appointment ->
                        eventPublisher.publishEvent(AppointmentEvent.of(type, appointment, bookingProperties.timeZone())));
            }
        });
    }
}
