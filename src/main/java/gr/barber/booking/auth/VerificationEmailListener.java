package gr.barber.booking.auth;

import gr.barber.booking.service.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class VerificationEmailListener {

    private static final Logger log = LoggerFactory.getLogger(VerificationEmailListener.class);

    private final EmailService emailService;

    public VerificationEmailListener(EmailService emailService) {
        this.emailService = emailService;
    }

    // Runs after commit: no email for a registration that rolled back, and a mail
    // server outage does not fail the registration (the user can ask for a resend).
    @TransactionalEventListener
    public void onVerificationEmailRequested(VerificationEmailRequested event) {
        try {
            emailService.sendVerificationEmail(event.email(), event.token());
        } catch (MailException e) {
            log.error("Could not send verification email to {}", event.email(), e);
        }
    }
}
