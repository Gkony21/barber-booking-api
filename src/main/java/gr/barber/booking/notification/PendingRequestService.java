package gr.barber.booking.notification;

import gr.barber.booking.config.BookingProperties;
import gr.barber.booking.model.Appointment;
import gr.barber.booking.model.AppointmentStatus;
import gr.barber.booking.model.Barber;
import gr.barber.booking.repository.AppointmentRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Requests waiting for the barber's answer: the barber is reminded once when one has waited a
 * while, and any still unanswered 2 hours before it starts is closed, so the customer hears
 * back and the time is free again. An unanswered change of time closes 2 hours before whichever
 * comes first, its new time or the confirmed one, which then simply stays.
 */
@Service
public class PendingRequestService {

    static final Duration REMIND_AFTER = Duration.ofMinutes(30);

    private final AppointmentRepository appointmentRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate transactions;
    private final BookingProperties bookingProperties;
    private final Clock clock;

    public PendingRequestService(AppointmentRepository appointmentRepository,
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

    /** Safe to call repeatedly; each request is reminded and expired at most once. */
    public void process() {
        Instant now = clock.instant();

        for (Appointment request : appointmentRepository.findPendingStartingBefore(now.plus(Barber.REQUEST_EXPIRY))) {
            transactions.executeWithoutResult(status -> appointmentRepository.findById(request.getId())
                    .filter(a -> a.getStatus() == AppointmentStatus.PENDING)
                    .ifPresent(a -> {
                        eventPublisher.publishEvent(AppointmentEvent.of(AppointmentEventType.REQUEST_EXPIRED, a, bookingProperties.timeZone()));
                        if (a.isChange()) {
                            appointmentRepository.delete(a); // the confirmed appointment keeps its time
                        } else {
                            a.expire();
                        }
                    }));
        }

        for (Appointment request : appointmentRepository.findPendingCreatedBefore(now.minus(REMIND_AFTER))) {
            transactions.executeWithoutResult(status -> {
                if (appointmentRepository.claimRequestReminder(request.getId(), now) == 1) {
                    appointmentRepository.findById(request.getId()).ifPresent(a -> eventPublisher.publishEvent(
                            AppointmentEvent.of(AppointmentEventType.REQUEST_WAITING, a, bookingProperties.timeZone())));
                }
            });
        }
    }
}
