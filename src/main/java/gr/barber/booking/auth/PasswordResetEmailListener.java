package gr.barber.booking.auth;

import gr.barber.booking.service.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class PasswordResetEmailListener {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetEmailListener.class);

    private final EmailService emailService;

    public PasswordResetEmailListener(EmailService emailService) {
        this.emailService = emailService;
    }

    @TransactionalEventListener
    public void onPasswordResetRequested(PasswordResetRequested event) {
        try {
            emailService.sendPasswordResetEmail(event.email(), event.token());
        } catch (MailException e) {
            log.error("Could not send password reset email", e);
        }
    }
}
