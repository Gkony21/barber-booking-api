package gr.barber.booking.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private final JavaMailSender mailSender;
    private final String from;
    private final String frontendUrl;

    public EmailService(JavaMailSender mailSender,
                        @Value("${app.mail.from}") String from,
                        @Value("${app.frontend-url}") String frontendUrl) {
        this.mailSender = mailSender;
        this.from = from;
        this.frontendUrl = frontendUrl;
    }

    public void sendVerificationEmail(String to, String token) {

        String link = frontendUrl + "/verify-email?token=" + token;

        // No name in the greeting: Greek names change form when addressed ("Γιώργος" → "Γιώργο").
        send(to, "Επιβεβαίωσε το email σου - FADE", """
                Γεια σου,

                Για να ενεργοποιήσεις τον λογαριασμό σου στο FADE, πάτησε τον παρακάτω σύνδεσμο:
                %s

                Ο σύνδεσμος ισχύει για 24 ώρες. Αν δεν έκανες εσύ εγγραφή, αγνόησε αυτό το μήνυμα.
                """.formatted(link));
    }

    public void sendPasswordResetEmail(String to, String token) {

        String link = frontendUrl + "/reset-password?token=" + token;

        send(to, "Αλλαγή κωδικού - FADE", """
                Γεια σου,

                Ζητήθηκε αλλαγή κωδικού για τον λογαριασμό σου στο FADE. Για να ορίσεις νέο κωδικό, πάτησε τον σύνδεσμο:
                %s

                Ο σύνδεσμος ισχύει για 1 ώρα και μόνο μία φορά. Αν δεν το ζήτησες εσύ, αγνόησε αυτό το μήνυμα: ο κωδικός σου δεν αλλάζει.
                """.formatted(link));
    }

    public void send(String to, String subject, String text) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);
        mailSender.send(message);
    }
}
