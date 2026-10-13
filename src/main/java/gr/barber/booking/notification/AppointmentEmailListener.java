package gr.barber.booking.notification;

import gr.barber.booking.service.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Emails customers and barbers about their appointments. Runs after commit, so nothing is
 * sent for a change that rolled back, and a mail failure never undoes a booking.
 */
@Component
public class AppointmentEmailListener {

    private static final Logger log = LoggerFactory.getLogger(AppointmentEmailListener.class);

    private static final Locale GREEK = Locale.forLanguageTag("el");
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy, HH:mm", GREEK);

    private final EmailService emailService;
    private final String frontendUrl;

    public AppointmentEmailListener(EmailService emailService,
                                    @Value("${app.frontend-url}") String frontendUrl) {
        this.emailService = emailService;
        this.frontendUrl = frontendUrl;
    }

    @TransactionalEventListener
    public void onAppointmentEvent(AppointmentEvent event) {

        String details = details(event);

        switch (event.type()) {
            case BOOKED_CONFIRMED -> {
                toCustomer(event, "Το ραντεβού σου επιβεβαιώθηκε",
                        "Το ραντεβού σου με τον/την " + event.barberName() + " επιβεβαιώθηκε.\n\n" + details);
                toBarber(event, "Νέο ραντεβού: " + when(event),
                        event.customerName() + " έκλεισε ραντεβού.\n\n" + details);
            }
            case BOOKED_PENDING -> {
                toCustomer(event, "Το αίτημά σου στάλθηκε",
                        "Το αίτημά σου για ραντεβού με τον/την " + event.barberName()
                                + " στάλθηκε και περιμένει επιβεβαίωση. Θα σε ενημερώσουμε με email.\n\n" + details);
                toBarber(event, "Νέο αίτημα ραντεβού: " + when(event),
                        event.customerName() + " ζητάει ραντεβού. Επιβεβαίωσέ το ή απόρριψέ το από την εφαρμογή.\n\n"
                                + details);
            }
            case CONFIRMED_BY_BARBER -> toCustomer(event, "Το ραντεβού σου επιβεβαιώθηκε",
                    "Ο/Η " + event.barberName() + " επιβεβαίωσε το ραντεβού σου.\n\n" + details);
            case REJECTED_BY_BARBER -> toCustomer(event, "Το αίτημά σου δεν έγινε δεκτό",
                    "Ο/Η " + event.barberName() + " δεν μπορεί να σε εξυπηρετήσει αυτή την ώρα. "
                            + "Μπορείς να διαλέξεις άλλη ώρα από την εφαρμογή.\n\n" + details);
            case CANCELLED_BY_CUSTOMER -> toBarber(event, "Ακύρωση ραντεβού: " + when(event),
                    event.customerName() + " ακύρωσε το ραντεβού. Η ώρα είναι ξανά διαθέσιμη.\n\n" + details);
            case CANCELLED_BY_BARBER -> toCustomer(event, "Το ραντεβού σου ακυρώθηκε",
                    "Ο/Η " + event.barberName() + " ακύρωσε το ραντεβού σου. "
                            + "Μπορείς να κλείσεις νέο ραντεβού από την εφαρμογή.\n\n" + details);
        }
    }

    private void toCustomer(AppointmentEvent event, String subject, String body) {
        send(event.customerEmail(), subject, "Γεια σου " + event.customerName() + ",\n\n" + body);
    }

    // Barbers also get the customer's phone, in case they need to reach them.
    private void toBarber(AppointmentEvent event, String subject, String body) {
        String phone = event.customerPhone() != null && !event.customerPhone().isBlank()
                ? "\nΤηλέφωνο πελάτη: " + event.customerPhone()
                : "";
        send(event.barberEmail(), subject, "Γεια σου " + event.barberName() + ",\n\n" + body + phone);
    }

    private void send(String to, String subject, String body) {
        try {
            emailService.send(to, subject, body + "\n\nΔες τα ραντεβού σου: " + frontendUrl + "/appointments\n");
        } catch (MailException e) {
            log.error("Could not send appointment email to {}", to, e);
        }
    }

    private static String details(AppointmentEvent event) {
        return "Υπηρεσία: " + event.serviceName() + "\n"
                + "Πότε: " + when(event) + "\n"
                + "Κουρέας: " + event.barberName() + "\n"
                + "Πελάτης: " + event.customerName() + "\n"
                + "Τιμή: " + String.format(GREEK, "%.2f €", event.price()) + " (πληρωμή στο κατάστημα)";
    }

    private static String when(AppointmentEvent event) {
        return WHEN.format(event.startTime());
    }
}
