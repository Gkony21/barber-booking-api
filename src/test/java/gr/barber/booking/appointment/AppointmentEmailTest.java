package gr.barber.booking.appointment;

import gr.barber.booking.model.Barber;
import gr.barber.booking.model.Role;
import gr.barber.booking.model.ServiceOffering;
import gr.barber.booking.model.User;
import gr.barber.booking.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.test.web.servlet.ResultActions;

import java.time.DayOfWeek;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AppointmentEmailTest extends IntegrationTest {

    private static final String SLOT = "2030-01-08T10:00";

    private User customer;
    private User barberUser;
    private Barber barber;
    private ServiceOffering haircut;

    @BeforeEach
    void setUp() {
        customer = createUser("Maria", "maria@test.gr", Role.CUSTOMER);
        barberUser = createUser("Nikos", "nikos@test.gr", Role.BARBER);
        barber = createApprovedBarber(barberUser, true);
        haircut = createService(barber, "Haircut", 30, "12.00");
        addShift(barber, DayOfWeek.TUESDAY, "10:00", "14:00");
    }

    @Test
    void autoConfirmedBookingEmailsBothCustomerAndBarber() throws Exception {
        book().andExpect(status().isCreated());

        List<SimpleMailMessage> mails = sentMails();
        assertThat(mails).hasSize(2);

        SimpleMailMessage toCustomer = mailTo(mails, "maria@test.gr");
        assertThat(toCustomer.getSubject()).isEqualTo("Το ραντεβού σου επιβεβαιώθηκε");
        assertThat(toCustomer.getText())
                .contains("Haircut", "Τρίτη 8 Ιανουαρίου 2030, 10:00", "12,00 €", "πληρωμή στο κατάστημα");

        SimpleMailMessage toBarber = mailTo(mails, "nikos@test.gr");
        assertThat(toBarber.getSubject()).isEqualTo("Νέο ραντεβού: Τρίτη 8 Ιανουαρίου 2030, 10:00");
        assertThat(toBarber.getText()).contains("Τηλέφωνο πελάτη: 6900000000");
        assertThat(toCustomer.getText()).doesNotContain("Τηλέφωνο πελάτη");
    }

    @Test
    void pendingRequestAsksBarberToRespondAndConfirmationNotifiesCustomer() throws Exception {
        barber.setAutoConfirmBookings(false);
        barberRepository.save(barber);

        long id = idOf(book());

        List<SimpleMailMessage> mails = sentMails();
        assertThat(mailTo(mails, "maria@test.gr").getSubject()).isEqualTo("Το αίτημά σου στάλθηκε");
        assertThat(mailTo(mails, "nikos@test.gr").getSubject()).startsWith("Νέο αίτημα ραντεβού");

        clearInvocations(mailSender);
        barberAction(id, "confirm").andExpect(status().isOk());

        assertThat(sentMails()).singleElement()
                .satisfies(mail -> {
                    assertThat(mail.getTo()).containsExactly("maria@test.gr");
                    assertThat(mail.getSubject()).isEqualTo("Το ραντεβού σου επιβεβαιώθηκε");
                });
    }

    @Test
    void rejectionNotifiesCustomer() throws Exception {
        barber.setAutoConfirmBookings(false);
        barberRepository.save(barber);
        long id = idOf(book());
        clearInvocations(mailSender);

        barberAction(id, "reject").andExpect(status().isOk());

        assertThat(sentMails()).singleElement()
                .satisfies(mail -> assertThat(mail.getSubject()).isEqualTo("Το αίτημά σου δεν έγινε δεκτό"));
    }

    @Test
    void cancellationNotifiesTheOtherSide() throws Exception {
        long first = idOf(book());
        clearInvocations(mailSender);

        mockMvc.perform(post("/appointments/{id}/cancel", first).header("Authorization", bearer(customer)))
                .andExpect(status().isOk());
        assertThat(sentMails()).singleElement()
                .satisfies(mail -> {
                    assertThat(mail.getTo()).containsExactly("nikos@test.gr");
                    assertThat(mail.getSubject()).startsWith("Ακύρωση ραντεβού");
                });

        long second = idOf(book());
        clearInvocations(mailSender);

        barberAction(second, "cancel").andExpect(status().isOk());
        assertThat(sentMails()).singleElement()
                .satisfies(mail -> {
                    assertThat(mail.getTo()).containsExactly("maria@test.gr");
                    assertThat(mail.getSubject()).isEqualTo("Το ραντεβού σου ακυρώθηκε");
                });
    }

    @Test
    void failedBookingSendsNothing() throws Exception {
        book().andExpect(status().isCreated());
        clearInvocations(mailSender);

        User other = createUser("Eleni", "eleni@test.gr", Role.CUSTOMER);
        book(other).andExpect(status().isConflict());

        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void mailServerOutageDoesNotBreakBooking() throws Exception {
        doThrow(new MailSendException("SMTP down")).when(mailSender).send(any(SimpleMailMessage.class));

        book().andExpect(status().isCreated());
    }

    // ---- helpers ----

    private ResultActions book() throws Exception {
        return book(customer);
    }

    private ResultActions book(User who) throws Exception {
        return mockMvc.perform(post("/appointments")
                .header("Authorization", bearer(who))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"barberId":%d,"serviceId":%d,"startTime":"%s"}""".formatted(barber.getId(), haircut.getId(), SLOT)));
    }

    private ResultActions barberAction(long id, String action) throws Exception {
        return mockMvc.perform(post("/barbers/me/appointments/{id}/" + action, id)
                .header("Authorization", bearer(barberUser)));
    }

    private List<SimpleMailMessage> sentMails() {
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, atLeast(0)).send(captor.capture());
        return captor.getAllValues();
    }

    private static SimpleMailMessage mailTo(List<SimpleMailMessage> mails, String address) {
        return mails.stream()
                .filter(mail -> List.of(mail.getTo()).contains(address))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No email sent to " + address));
    }

    private static long idOf(ResultActions result) throws Exception {
        String body = result.andReturn().getResponse().getContentAsString();
        return Long.parseLong(body.replaceAll("(?s)^\\{\"id\":(\\d+).*", "$1"));
    }
}
