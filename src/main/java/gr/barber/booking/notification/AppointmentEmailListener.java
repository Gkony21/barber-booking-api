package gr.barber.booking.notification;

import gr.barber.booking.service.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Async;
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

    @Async
    @TransactionalEventListener
    public void onAppointmentEvent(AppointmentEvent event) {

        String details = details(event);

        switch (event.type()) {
            case BOOKED_CONFIRMED -> {
                toCustomer(event, "Το ραντεβού σου επιβεβαιώθηκε",
                        "Το ραντεβού σου κλείστηκε. Τα στοιχεία:\n\n" + details);
                toBarber(event, "Νέο ραντεβού: " + when(event),
                        "Έκλεισε νέο ραντεβού στο πρόγραμμά σου.\n\n" + details);
            }
            case BOOKED_PENDING -> {
                toCustomer(event, "Το αίτημά σου στάλθηκε",
                        "Το αίτημά σου για ραντεβού στάλθηκε και περιμένει επιβεβαίωση. "
                                + "Θα σου στείλουμε email μόλις απαντηθεί.\n\n" + details);
                toBarber(event, "Νέο αίτημα ραντεβού: " + when(event),
                        "Υπάρχει νέο αίτημα για ραντεβού. Αποδέξου το ή απόρριψέ το από το Πρόγραμμα στην εφαρμογή.\n\n"
                                + details);
            }
            case CONFIRMED_BY_BARBER -> toCustomer(event, "Το ραντεβού σου επιβεβαιώθηκε",
                    "Το αίτημά σου έγινε δεκτό και το ραντεβού είναι κλεισμένο.\n\n" + details);
            case REJECTED_BY_BARBER -> toCustomer(event, "Το αίτημά σου δεν έγινε δεκτό",
                    "Η ώρα που ζήτησες δεν είναι διαθέσιμη. Μπορείς να διαλέξεις άλλη ώρα από την εφαρμογή.\n\n"
                            + details);
            case CANCELLED_BY_CUSTOMER -> toBarber(event, "Ακύρωση ραντεβού: " + when(event),
                    "Ένα ραντεβού ακυρώθηκε και η ώρα είναι ξανά ελεύθερη.\n\n" + details);
            case CANCELLED_BY_BARBER -> toCustomer(event, "Το ραντεβού σου ακυρώθηκε",
                    "Το ραντεβού σου ακυρώθηκε. Μπορείς να κλείσεις νέο ραντεβού από την εφαρμογή.\n\n" + details);
            case RESCHEDULED_BY_CUSTOMER -> {
                boolean pending = event.pending();
                toCustomer(event, pending ? "Η αλλαγή ώρας στάλθηκε" : "Η νέα ώρα του ραντεβού σου",
                        (pending ? "Η νέα ώρα περιμένει επιβεβαίωση. Θα σου στείλουμε email μόλις απαντηθεί."
                                : "Το ραντεβού σου μετακινήθηκε. Τα νέα στοιχεία:") + "\n\n" + details);
                toBarber(event, "Αλλαγή ώρας ραντεβού: " + when(event),
                        (pending ? "Ένα ραντεβού ζητά να μετακινηθεί και περιμένει την αποδοχή σου."
                                : "Ένα ραντεβού μετακινήθηκε σε νέα ώρα.") + "\n\n" + details);
            }
            case CHANGE_REQUESTED -> {
                toCustomer(event, "Η αλλαγή ώρας στάλθηκε",
                        "Ζήτησες νέα ώρα για το ραντεβού σου. Μέχρι να απαντηθεί, ισχύει η αρχική ώρα. "
                                + "Θα σου στείλουμε email μόλις απαντηθεί.\n\n" + details);
                toBarber(event, "Αίτημα αλλαγής ώρας: " + when(event),
                        "Ένα ραντεβού ζητά να μετακινηθεί. Αποδέξου ή απόρριψε την αλλαγή από το Πρόγραμμα στην εφαρμογή. "
                                + "Αν την απορρίψεις, το ραντεβού μένει στην αρχική ώρα.\n\n" + details);
            }
            case CHANGE_ACCEPTED -> toCustomer(event, "Η αλλαγή ώρας έγινε δεκτή",
                    "Το ραντεβού σου μετακινήθηκε στη νέα ώρα.\n\n" + details);
            case CHANGE_DECLINED -> toCustomer(event, "Η αλλαγή ώρας δεν έγινε δεκτή",
                    "Η νέα ώρα δεν είναι διαθέσιμη, οπότε το ραντεβού σου μένει στην αρχική ώρα. "
                            + "Μπορείς να ζητήσεις άλλη ώρα από την εφαρμογή.\n\n" + details);
            case CHANGE_WITHDRAWN -> toBarber(event, "Η αλλαγή ώρας ακυρώθηκε",
                    "Το αίτημα αλλαγής ώρας ακυρώθηκε. Το ραντεβού μένει στην αρχική ώρα.\n\n" + details);
            case REQUEST_WAITING -> {
                if (event.originalStartTime() != null) {
                    toBarber(event, "Αίτημα αλλαγής περιμένει απάντηση: " + when(event),
                            "Ένα αίτημα αλλαγής ώρας περιμένει την απάντησή σου. Αν δεν απαντηθεί έγκαιρα, "
                                    + "το ραντεβού μένει στην αρχική ώρα.\n\n" + details);
                } else {
                    toBarber(event, "Αίτημα περιμένει απάντηση: " + when(event),
                            "Ένα αίτημα για ραντεβού περιμένει την απάντησή σου. Αν δεν απαντηθεί έως 2 ώρες πριν από την ώρα του, "
                                    + "ακυρώνεται αυτόματα.\n\n" + details);
                }
            }
            case REQUEST_EXPIRED -> {
                if (event.originalStartTime() != null) {
                    toCustomer(event, "Η αλλαγή ώρας δεν απαντήθηκε",
                            "Το αίτημα αλλαγής δεν απαντήθηκε εγκαίρως, οπότε το ραντεβού σου μένει στην αρχική ώρα.\n\n"
                                    + details);
                    toBarber(event, "Αίτημα αλλαγής έκλεισε χωρίς απάντηση",
                            "Ένα αίτημα αλλαγής ώρας δεν απαντήθηκε εγκαίρως και έκλεισε. Ισχύει η αρχική ώρα.\n\n" + details);
                } else {
                    toCustomer(event, "Το αίτημά σου δεν επιβεβαιώθηκε",
                            "Το αίτημα δεν απαντήθηκε εγκαίρως, οπότε ακυρώθηκε και η ώρα ελευθερώθηκε. "
                                    + "Μπορείς να κλείσεις άλλη ώρα από την εφαρμογή.\n\n" + details);
                    toBarber(event, "Αίτημα ακυρώθηκε χωρίς απάντηση: " + when(event),
                            "Ένα αίτημα δεν απαντήθηκε έως 2 ώρες πριν και ακυρώθηκε αυτόματα. Στάλθηκε ενημέρωση με email.\n\n"
                                    + details);
                }
            }
            case REMINDER_DAY -> toCustomer(event, "Υπενθύμιση ραντεβού: " + when(event),
                    "Το ραντεβού σου πλησιάζει. Αν δεν μπορείς να έρθεις, ακύρωσέ το από την εφαρμογή "
                            + "έως 2 ώρες πριν, ώστε να μπορεί να κλείσει την ώρα κάποιος άλλος.\n\n" + details);
            case REMINDER_SOON -> toCustomer(event, "Σε λίγο: ραντεβού στις " + event.startTime().toLocalTime(),
                    "Το ραντεβού σου είναι σε περίπου 2 ώρες. Τα λέμε εκεί!\n\n" + details);
        }
    }

    // Greetings and sentences never contain names: Greek names change form ("Γιώργος" → "Γιώργο")
    // and words around them depend on gender, so names only appear as labelled values.
    private void toCustomer(AppointmentEvent event, String subject, String body) {
        if (event.customerEmail() == null) {
            return; // phone or walk-in customer without an account
        }
        send(event.customerEmail(), subject, "Γεια σου,\n\n" + body, "Δες τα ραντεβού σου: " + frontendUrl + "/appointments");
    }

    // Barbers also get the customer's phone, in case they need to reach them.
    private void toBarber(AppointmentEvent event, String subject, String body) {
        String phone = event.customerPhone() != null && !event.customerPhone().isBlank()
                ? "\nΤηλέφωνο πελάτη: " + event.customerPhone()
                : "";
        // Barbers manage bookings from their schedule, not the customers' appointments page.
        send(event.barberEmail(), subject, "Γεια σου,\n\n" + body + phone, "Δες το πρόγραμμά σου: " + frontendUrl + "/barber");
    }

    private void send(String to, String subject, String body, String link) {
        try {
            emailService.send(to, subject, body + "\n\n" + link + "\n");
        } catch (MailException e) {
            log.error("Could not send appointment email to {}", to, e);
        }
    }

    private static String details(AppointmentEvent event) {
        String extras = event.extraNames().isEmpty() ? "" : "Έξτρα: " + String.join(", ", event.extraNames()) + "\n";
        return "Υπηρεσία: " + event.serviceName() + "\n"
                + extras
                + (event.originalStartTime() != null
                        ? "Αρχική ώρα: " + WHEN.format(event.originalStartTime()) + "\nΝέα ώρα: " + when(event) + "\n"
                        : "Πότε: " + when(event) + "\n")
                + "Barber: " + event.barberName() + "\n"
                + "Πελάτης: " + event.customerName() + "\n"
                + "Τιμή: " + String.format(GREEK, "%.2f €", event.price()) + " (πληρωμή στο κατάστημα)";
    }

    private static String when(AppointmentEvent event) {
        return WHEN.format(event.startTime());
    }
}
