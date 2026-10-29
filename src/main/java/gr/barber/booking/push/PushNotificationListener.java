package gr.barber.booking.push;

import gr.barber.booking.notification.AppointmentEvent;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Tells the barber's phone right away about what needs their attention: a new booking or request,
 * a change of time and a cancellation by the customer. Runs after commit, like the emails.
 */
@Component
public class PushNotificationListener {

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("EEEE d MMMM, HH:mm", Locale.forLanguageTag("el"));

    private final PushService pushService;

    public PushNotificationListener(PushService pushService) {
        this.pushService = pushService;
    }

    @Async
    @TransactionalEventListener
    public void onAppointmentEvent(AppointmentEvent event) {
        PushMessage message = messageFor(event);
        if (message != null) {
            pushService.notifyUser(event.barberEmail(), message);
        }
    }

    // Names only as labels at the end, never inside a sentence (see the email listener).
    static PushMessage messageFor(AppointmentEvent event) {
        String details = when(event.startTime()) + " · " + event.serviceName() + " · " + event.customerName();
        return switch (event.type()) {
            case BOOKED_CONFIRMED -> new PushMessage("Νέο ραντεβού", details, "/barber");
            case BOOKED_PENDING -> new PushMessage("Νέο αίτημα ραντεβού", details + " · Αποδέξου ή απόρριψέ το", "/barber");
            case CHANGE_REQUESTED -> new PushMessage("Αίτημα αλλαγής ώρας",
                    when(event.originalStartTime()) + " → " + when(event.startTime()) + " · " + event.customerName(), "/barber");
            case RESCHEDULED_BY_CUSTOMER -> new PushMessage("Αλλαγή ώρας ραντεβού", details, "/barber");
            case CANCELLED_BY_CUSTOMER -> new PushMessage("Ακύρωση ραντεβού", details, "/barber");
            default -> null;
        };
    }

    private static String when(ZonedDateTime time) {
        String text = WHEN.format(time);
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}
