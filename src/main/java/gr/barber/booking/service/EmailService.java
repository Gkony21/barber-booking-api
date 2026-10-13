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

    public void sendVerificationEmail(String to, String name, String token) {

        String link = frontendUrl + "/verify-email?token=" + token;

        send(to, "Επιβεβαίωση email - Barber Booking", """
                Γεια σου %s,

                Για να ενεργοποιήσεις τον λογαριασμό σου, πάτησε τον παρακάτω σύνδεσμο:
                %s

                Ο σύνδεσμος λήγει σε 24 ώρες. Αν δεν έκανες εσύ εγγραφή, αγνόησε αυτό το μήνυμα.
                """.formatted(name, link));
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
