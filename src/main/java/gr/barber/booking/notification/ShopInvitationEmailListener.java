package gr.barber.booking.notification;

import gr.barber.booking.service.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class ShopInvitationEmailListener {

    private static final Logger log = LoggerFactory.getLogger(ShopInvitationEmailListener.class);

    private final EmailService emailService;
    private final String frontendUrl;

    public ShopInvitationEmailListener(EmailService emailService, @Value("${app.frontend-url}") String frontendUrl) {
        this.emailService = emailService;
        this.frontendUrl = frontendUrl;
    }

    // The shop name is shown as a labelled value, never inside a sentence (see AppointmentEmailListener).
    @Async
    @TransactionalEventListener
    public void onInvitation(ShopInvitationSent event) {
        try {
            emailService.send(event.barberEmail(), "Πρόσκληση σε κατάστημα - FADE", """
                    Γεια σου,

                    Έχεις πρόσκληση να μπεις σε κατάστημα στο FADE. Αν τη δεχτείς, το κατάστημα θα φαίνεται στο προφίλ σου.

                    Κατάστημα: %s
                    Πόλη: %s

                    Αποδέξου ή απόρριψε την πρόσκληση από το Προφίλ στην εφαρμογή:
                    %s/barber/profile
                    """.formatted(event.shopName(), event.shopCity(), frontendUrl));
        } catch (MailException e) {
            log.error("Could not send shop invitation email", e);
        }
    }
}
