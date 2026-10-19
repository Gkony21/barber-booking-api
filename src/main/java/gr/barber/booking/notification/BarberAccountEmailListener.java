package gr.barber.booking.notification;

import gr.barber.booking.model.Role;
import gr.barber.booking.model.User;
import gr.barber.booking.repository.UserRepository;
import gr.barber.booking.service.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/** Tells admins about new barbers to review, and barbers when they are approved. */
@Component
public class BarberAccountEmailListener {

    private static final Logger log = LoggerFactory.getLogger(BarberAccountEmailListener.class);

    private final EmailService emailService;
    private final UserRepository userRepository;
    private final String frontendUrl;

    public BarberAccountEmailListener(EmailService emailService,
                                      UserRepository userRepository,
                                      @Value("${app.frontend-url}") String frontendUrl) {
        this.emailService = emailService;
        this.userRepository = userRepository;
        this.frontendUrl = frontendUrl;
    }

    // Names and details are labelled values, never part of a sentence.
    @Async
    @TransactionalEventListener
    public void onSignedUp(BarberSignedUp event) {
        String body = """
                Γεια σου,

                Νέο προφίλ barber περιμένει έγκριση.

                Όνομα: %s
                Email: %s
                Τηλέφωνο: %s
                Πόλη: %s

                Έγκριση από τη σελίδα του admin:
                %s/admin
                """.formatted(event.name(), event.email(), orDash(event.phoneNumber()), orDash(event.city()), frontendUrl);
        for (User admin : userRepository.findAllByRole(Role.ADMIN)) {
            send(admin.getEmail(), "Νέο προφίλ barber για έγκριση - FADE", body);
        }
    }

    @Async
    @TransactionalEventListener
    public void onApproved(BarberApproved event) {
        send(event.email(), "Το προφίλ σου εγκρίθηκε - FADE", """
                Γεια σου,

                Το προφίλ σου στο FADE εγκρίθηκε και οι πελάτες μπορούν πλέον να σε βρουν και να κλείσουν ραντεβού.

                Αν δεν το έχεις κάνει ακόμα, πρόσθεσε υπηρεσίες και ωράριο, ώστε να εμφανίζεσαι στη λίστα:
                %s/barber
                """.formatted(frontendUrl));
    }

    private void send(String to, String subject, String body) {
        try {
            emailService.send(to, subject, body);
        } catch (MailException e) {
            log.error("Could not send barber account email", e);
        }
    }

    private static String orDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}
