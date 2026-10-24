package gr.barber.booking.appointment;

import com.jayway.jsonpath.JsonPath;
import gr.barber.booking.model.Barber;
import gr.barber.booking.model.Role;
import gr.barber.booking.model.ServiceKind;
import gr.barber.booking.model.ServiceOffering;
import gr.barber.booking.model.User;
import gr.barber.booking.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.test.web.servlet.ResultActions;

import java.time.DayOfWeek;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The barber moves or changes appointments, e.g. after a phone call. "Now" is Monday 7 January 2030,
 * 10:00 Athens; the barber works Tuesdays 10:00-18:00.
 */
class BarberEditTest extends IntegrationTest {

    private static final String TUESDAY = "2030-01-08";

    private User barberUser;
    private User customer;
    private Barber barber;
    private ServiceOffering haircut;
    private ServiceOffering beard;
    private ServiceOffering wash;

    @BeforeEach
    void setUp() {
        customer = createUser("Maria", "maria@test.gr", Role.CUSTOMER);
        barberUser = createUser("Nikos", "nikos@test.gr", Role.BARBER);
        barber = createApprovedBarber(barberUser, true);
        haircut = createService(barber, "Haircut", 30, "12.00");
        beard = createService(barber, "Haircut + beard", 40, "15.00");
        wash = createService(barber, "Wash", 10, "2.00");
        wash.setKind(ServiceKind.EXTRA);
        serviceOfferingRepository.save(wash);
        addShift(barber, DayOfWeek.TUESDAY, "10:00", "18:00");
    }

    @Test
    void theBarberMovesAnAppBookingAndTheCustomerIsTold() throws Exception {
        long id = idOf(book(TUESDAY + "T10:00"));
        clearInvocations(mailSender);

        update(id, haircut, TUESDAY + "T15:00", "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.startTime").value("2030-01-08T15:00:00+02:00"))
                .andExpect(jsonPath("$.customerName").value("Maria"))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        SimpleMailMessage mail = lastMail();
        assertThat(mail.getTo()).containsExactly("maria@test.gr");
        assertThat(mail.getSubject()).isEqualTo("Το ραντεβού σου άλλαξε");
        assertThat(mail.getText()).contains("Αρχική ώρα: Τρίτη 8 Ιανουαρίου 2030, 10:00", "Νέα ώρα: Τρίτη 8 Ιανουαρίου 2030, 15:00");

        // The old time is free again for everyone.
        mockMvc.perform(get("/barbers/{id}/availability", barber.getId()).param("serviceId", haircut.getId().toString()).param("date", TUESDAY))
                .andExpect(jsonPath("$.startTimes", hasItems("10:00")));
    }

    @Test
    void changingServicesRecalculatesLengthAndPriceAndTellsTheCustomer() throws Exception {
        long id = idOf(book(TUESDAY + "T10:00"));
        clearInvocations(mailSender);

        update(id, beard, TUESDAY + "T10:00", String.valueOf(wash.getId()))
                .andExpect(jsonPath("$.serviceName").value("Haircut + beard"))
                .andExpect(jsonPath("$.extras", hasSize(1)))
                .andExpect(jsonPath("$.durationMinutes").value(50))
                .andExpect(jsonPath("$.price").value(17.00));
        assertThat(lastMail().getText()).contains("Πότε: Τρίτη 8 Ιανουαρίου 2030, 10:00").doesNotContain("Αρχική ώρα");

        // Saving it unchanged sends nothing.
        clearInvocations(mailSender);
        update(id, beard, TUESDAY + "T10:00", String.valueOf(wash.getId())).andExpect(status().isOk());
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void phoneBookingsCanBeEditedCompletelyWithoutAnyEmail() throws Exception {
        long id = idOf(manual(TUESDAY + "T11:00", "Κώστας"));

        mockMvc.perform(put("/barbers/me/appointments/{id}", id).header("Authorization", bearer(barberUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"serviceId":%d,"startTime":"%sT20:30","customerName":" Κώστας Π. ","customerPhone":"6911111111","note":"φέρνει τον γιο του"}"""
                                .formatted(haircut.getId(), TUESDAY)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.startTime").value("2030-01-08T20:30:00+02:00")) // after closing, the barber's call
                .andExpect(jsonPath("$.customerName").value("Κώστας Π."))
                .andExpect(jsonPath("$.customerPhone").value("6911111111"))
                .andExpect(jsonPath("$.note").value("φέρνει τον γιο του"));

        mockMvc.perform(put("/barbers/me/appointments/{id}", id).header("Authorization", bearer(barberUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"serviceId\":%d,\"startTime\":\"%sT11:00\",\"customerName\":\" \"}".formatted(haircut.getId(), TUESDAY)))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void movesFollowTheRules() throws Exception {
        long id = idOf(book(TUESDAY + "T10:00"));
        manual(TUESDAY + "T12:00", "Κώστας");

        update(id, haircut, TUESDAY + "T11:45", "").andExpect(jsonPath("$.code").value("OVERLAPS_APPOINTMENT"));
        update(id, haircut, "2030-01-07T09:00", "").andExpect(jsonPath("$.code").value("APPOINTMENT_IN_PAST"));
        // Moving within its own time is fine.
        update(id, haircut, TUESDAY + "T10:15", "").andExpect(status().isOk());

        // Someone else's appointment is not the barber's to change.
        User other = createUser("Giorgos", "giorgos@test.gr", Role.BARBER);
        createApprovedBarber(other, true);
        mockMvc.perform(put("/barbers/me/appointments/{id}", id).header("Authorization", bearer(other))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"serviceId\":%d,\"startTime\":\"%sT16:00\"}".formatted(haircut.getId(), TUESDAY)))
                .andExpect(status().isNotFound());
    }

    @Test
    void requestsAreAnsweredNotMovedAndAWaitingChangeComesFirst() throws Exception {
        barber.setAutoConfirmBookings(false);
        barberRepository.save(barber);
        long id = idOf(book(TUESDAY + "T10:00"));
        update(id, haircut, TUESDAY + "T15:00", "").andExpect(jsonPath("$.code").value("APPOINTMENT_NOT_EDITABLE"));

        mockMvc.perform(post("/barbers/me/appointments/{id}/confirm", id).header("Authorization", bearer(barberUser)));
        mockMvc.perform(post("/appointments/{id}/reschedule", id).header("Authorization", bearer(customer))
                .contentType(MediaType.APPLICATION_JSON).content("{\"startTime\":\"%sT16:00\"}".formatted(TUESDAY)));
        update(id, haircut, TUESDAY + "T15:00", "").andExpect(jsonPath("$.code").value("CHANGE_PENDING"));
    }

    @Test
    void theBarbersFreeTimesCanLeaveOutTheAppointmentBeingMoved() throws Exception {
        long id = idOf(book(TUESDAY + "T10:00"));

        mockMvc.perform(get("/barbers/me/availability").header("Authorization", bearer(barberUser))
                        .param("minutes", "30").param("date", TUESDAY).param("except", String.valueOf(id)))
                .andExpect(jsonPath("$.startTimes", hasItems("10:00", "10:15")));
        mockMvc.perform(get("/barbers/me/availability").header("Authorization", bearer(barberUser))
                        .param("minutes", "30").param("date", TUESDAY))
                .andExpect(jsonPath("$.startTimes[0]").value("10:30"));
    }

    // ---- helpers ----

    private SimpleMailMessage lastMail() {
        ArgumentCaptor<SimpleMailMessage> mails = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, atLeastOnce()).send(mails.capture());
        return mails.getValue();
    }

    private ResultActions book(String startTime) throws Exception {
        return mockMvc.perform(post("/appointments").header("Authorization", bearer(customer))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"barberId\":%d,\"serviceId\":%d,\"startTime\":\"%s\"}".formatted(barber.getId(), haircut.getId(), startTime)));
    }

    private ResultActions manual(String startTime, String name) throws Exception {
        return mockMvc.perform(post("/barbers/me/appointments").header("Authorization", bearer(barberUser))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"serviceId\":%d,\"startTime\":\"%s\",\"customerName\":\"%s\"}".formatted(haircut.getId(), startTime, name)));
    }

    private ResultActions update(long id, ServiceOffering service, String startTime, String extraIds) throws Exception {
        return mockMvc.perform(put("/barbers/me/appointments/{id}", id).header("Authorization", bearer(barberUser))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"serviceId\":%d,\"startTime\":\"%s\",\"extraIds\":[%s]}".formatted(service.getId(), startTime, extraIds)));
    }

    private static long idOf(ResultActions result) throws Exception {
        return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id")).longValue();
    }
}
