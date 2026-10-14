package gr.barber.booking.notification;

import gr.barber.booking.model.Appointment;
import gr.barber.booking.model.AppointmentStatus;
import gr.barber.booking.model.Barber;
import gr.barber.booking.model.Role;
import gr.barber.booking.model.ServiceOffering;
import gr.barber.booking.model.User;
import gr.barber.booking.repository.AppointmentRepository;
import gr.barber.booking.support.IntegrationTest;
import gr.barber.booking.support.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;

/** "Now" is Monday 7 January 2030, 10:00 Athens. */
class ReminderTest extends IntegrationTest {

    private static final Instant NOW = TestcontainersConfiguration.NOW;

    @Autowired
    private AppointmentReminderService reminders;

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private User customer;
    private Barber barber;
    private ServiceOffering haircut;

    @BeforeEach
    void setUp() {
        customer = createUser("Maria", "maria@test.gr", Role.CUSTOMER);
        barber = createApprovedBarber(createUser("Nikos", "nikos@test.gr", Role.BARBER), true);
        haircut = createService(barber, "Haircut", 30, "12.00");
    }

    @Test
    void remindsTheDayBeforeAndTwoHoursBeforeOnceEach() throws Exception {
        Appointment tomorrow = appointment(NOW.plus(Duration.ofHours(20)), AppointmentStatus.CONFIRMED, NOW.minus(Duration.ofDays(3)));

        reminders.sendDueReminders();
        reminders.sendDueReminders(); // a second run sends nothing new

        List<SimpleMailMessage> mails = sentMails();
        assertThat(mails).singleElement().satisfies(mail -> {
            assertThat(mail.getTo()).containsExactly("maria@test.gr");
            assertThat(mail.getSubject()).startsWith("Υπενθύμιση ραντεβού: Τρίτη 8 Ιανουαρίου 2030");
            assertThat(mail.getText()).contains("έως 2 ώρες πριν", "Haircut");
        });

        clearInvocations(mailSender);
        clock.set(tomorrow.getStartAt().minus(Duration.ofMinutes(110)));
        reminders.sendDueReminders();

        assertThat(sentMails()).singleElement()
                .satisfies(mail -> assertThat(mail.getSubject()).isEqualTo("Σε λίγο: ραντεβού στις 06:00"));
    }

    @Test
    void skipsRemindersForBookingsMadeInsideTheWindowAndForPendingOrCancelled() throws Exception {
        appointment(NOW.plus(Duration.ofHours(10)), AppointmentStatus.CONFIRMED, NOW.minus(Duration.ofHours(1)));
        appointment(NOW.plus(Duration.ofHours(20)), AppointmentStatus.PENDING, NOW.minus(Duration.ofDays(3)));
        appointment(NOW.plus(Duration.ofHours(21)), AppointmentStatus.CANCELLED, NOW.minus(Duration.ofDays(3)));

        reminders.sendDueReminders();

        assertThat(sentMails()).isEmpty();
    }

    private Appointment appointment(Instant start, AppointmentStatus status, Instant createdAt) {
        Appointment appointment = appointmentRepository.save(
                new Appointment(customer, barber, haircut, start, start.plus(Duration.ofMinutes(30)), status, null));
        jdbc.update("update appointments set created_at = ? where id = ?", java.sql.Timestamp.from(createdAt), appointment.getId());
        return appointment;
    }

    private List<SimpleMailMessage> sentMails() {
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, atLeast(0)).send(captor.capture());
        return captor.getAllValues();
    }
}
