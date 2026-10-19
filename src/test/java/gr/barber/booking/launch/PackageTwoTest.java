package gr.barber.booking.launch;

import com.jayway.jsonpath.JsonPath;
import gr.barber.booking.model.Barber;
import gr.barber.booking.model.Role;
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
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** "Now" is Monday 7 January 2030, 10:00 Athens; the barber works Tuesdays 10:00-18:00, 15-minute starts. */
class PackageTwoTest extends IntegrationTest {

    private static final String TUESDAY = "2030-01-08";

    private User customer;
    private User other;
    private User barberUser;
    private Barber barber;
    private ServiceOffering haircut;

    @BeforeEach
    void setUp() {
        customer = createUser("Maria", "maria@test.gr", Role.CUSTOMER);
        other = createUser("Eleni", "eleni@test.gr", Role.CUSTOMER);
        barberUser = createUser("Nikos", "nikos@test.gr", Role.BARBER);
        barber = createApprovedBarber(barberUser, true);
        barber.setSlotStepMinutes(15);
        barber.setCity("Αθήνα");
        barberRepository.save(barber);
        haircut = createService(barber, "Haircut", 30, "12.00");
        addShift(barber, DayOfWeek.TUESDAY, "10:00", "18:00");
    }

    // ---- rescheduling ----

    @Test
    void customersMoveAnAppointmentAndTheBarberIsTold() throws Exception {
        long id = idOf(book(customer, TUESDAY + "T10:00"));
        clearInvocations(mailSender);

        // Moving by 15 minutes overlaps its own old time, which is fine.
        reschedule(customer, id, TUESDAY + "T10:15")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.startTime").value("2030-01-08T10:15:00+02:00"))
                .andExpect(jsonPath("$.endTime").value("2030-01-08T10:45:00+02:00"))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        ArgumentCaptor<SimpleMailMessage> mails = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, atLeastOnce()).send(mails.capture());
        assertThat(mails.getAllValues()).extracting(SimpleMailMessage::getSubject).contains("Αλλαγή ώρας ραντεβού: Τρίτη 8 Ιανουαρίου 2030, 10:15");

        // The old time is free again; free times can also be asked for by length, as the app does when moving.
        availability(TUESDAY).andExpect(jsonPath("$.startTimes", hasItem("10:45")));
        mockMvc.perform(get("/barbers/{id}/availability", barber.getId()).param("minutes", "30").param("date", TUESDAY))
                .andExpect(jsonPath("$.durationMinutes").value(30))
                .andExpect(jsonPath("$.startTimes", not(hasItem("10:15"))));
        mockMvc.perform(get("/barbers/{id}/availability", barber.getId()).param("date", TUESDAY))
                .andExpect(status().isBadRequest());
        book(other, TUESDAY + "T11:00").andExpect(status().isCreated());
        reschedule(customer, id, TUESDAY + "T11:00").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SLOT_TAKEN"));
    }

    @Test
    void movingFollowsTheBookingRules() throws Exception {
        long id = idOf(book(customer, TUESDAY + "T10:00"));

        reschedule(customer, id, TUESDAY + "T19:00").andExpect(jsonPath("$.code").value("BARBER_NOT_WORKING"));
        reschedule(customer, id, "2030-01-07T10:30").andExpect(jsonPath("$.code").value("TOO_SOON"));

        clock.set(Instant.parse("2030-01-08T06:30:00Z")); // Tuesday 08:30 Athens, 1.5 hours before
        reschedule(customer, id, TUESDAY + "T15:00").andExpect(jsonPath("$.code").value("CHANGE_TOO_LATE"));
    }

    @Test
    void aBarberWhoApprovesByHandAnswersTheNewTimeAgain() throws Exception {
        long id = idOf(book(customer, TUESDAY + "T10:00"));
        barber.setAutoConfirmBookings(false);
        barberRepository.save(barber);

        reschedule(customer, id, TUESDAY + "T15:00").andExpect(jsonPath("$.status").value("PENDING"));
    }

    // ---- blocks of hours ----

    @Test
    void blockedHoursCannotBeBooked() throws Exception {
        timeOff("{\"startDate\":\"%s\",\"endDate\":\"%s\",\"startTime\":\"12:00\",\"endTime\":\"14:00\",\"reason\":\"Γιατρός\"}"
                .formatted(TUESDAY, TUESDAY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.startTime").value("12:00"))
                .andExpect(jsonPath("$.endTime").value("14:00"));

        availability(TUESDAY)
                .andExpect(jsonPath("$.startTimes", hasItem("11:30")))
                .andExpect(jsonPath("$.startTimes", not(hasItem("11:45"))))
                .andExpect(jsonPath("$.startTimes", not(hasItem("13:30"))))
                .andExpect(jsonPath("$.startTimes", hasItem("14:00")));
        book(customer, TUESDAY + "T12:30").andExpect(jsonPath("$.code").value("BARBER_NOT_WORKING"));

        timeOff("{\"startDate\":\"2030-01-08\",\"endDate\":\"2030-01-09\",\"startTime\":\"12:00\",\"endTime\":\"14:00\"}")
                .andExpect(jsonPath("$.code").value("INVALID_BLOCK"));
    }

    // ---- break between appointments ----

    @Test
    void theBarbersBreakIsKeptFreeAfterEachAppointment() throws Exception {
        book(customer, TUESDAY + "T10:00").andExpect(status().isCreated());
        mockMvc.perform(put("/barbers/me").header("Authorization", bearer(barberUser))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"bufferMinutes\":15}"))
                .andExpect(jsonPath("$.bufferMinutes").value(15));

        availability(TUESDAY)
                .andExpect(jsonPath("$.startTimes", not(hasItem("10:30"))))
                .andExpect(jsonPath("$.startTimes", hasItem("10:45")));
        book(other, TUESDAY + "T10:30").andExpect(status().isConflict());
    }

    // ---- no-shows ----

    @Test
    void barbersMarkNoShowsAndSeeThemOnTheCustomersNextBookings() throws Exception {
        long missed = idOf(book(customer, TUESDAY + "T10:00"));
        book(customer, TUESDAY + "T15:00").andExpect(status().isCreated());

        noShow(missed).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("NOT_STARTED_YET"));

        clock.set(Instant.parse("2030-01-08T08:20:00Z")); // Tuesday 10:20 Athens
        noShow(missed).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("NO_SHOW"));

        mockMvc.perform(get("/barbers/me/appointments").param("from", TUESDAY).param("to", TUESDAY)
                        .header("Authorization", bearer(barberUser)))
                .andExpect(jsonPath("$[1].customerNoShows").value(1));
    }

    // ---- city search ----

    @Test
    void citySearchIgnoresAccentsAndCaseAndMatchesPartOfTheName() throws Exception {
        for (String search : new String[]{"Αθήνα", "αθηνα", "ΑΘΗΝΑ", "Αθ"}) {
            mockMvc.perform(get("/barbers").param("city", search)).andExpect(jsonPath("$", hasSize(1)));
        }
        mockMvc.perform(get("/barbers").param("city", "Πάτρα")).andExpect(jsonPath("$", hasSize(0)));
    }

    // ---- helpers ----

    private ResultActions availability(String date) throws Exception {
        return mockMvc.perform(get("/barbers/{id}/availability", barber.getId())
                .param("serviceId", haircut.getId().toString()).param("date", date));
    }

    private ResultActions book(User who, String startTime) throws Exception {
        return mockMvc.perform(post("/appointments").header("Authorization", bearer(who))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"barberId\":%d,\"serviceId\":%d,\"startTime\":\"%s\"}".formatted(barber.getId(), haircut.getId(), startTime)));
    }

    private ResultActions reschedule(User who, long id, String startTime) throws Exception {
        return mockMvc.perform(post("/appointments/{id}/reschedule", id).header("Authorization", bearer(who))
                .contentType(MediaType.APPLICATION_JSON).content("{\"startTime\":\"%s\"}".formatted(startTime)));
    }

    private ResultActions timeOff(String json) throws Exception {
        return mockMvc.perform(post("/barbers/me/time-off").header("Authorization", bearer(barberUser))
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions noShow(long id) throws Exception {
        return mockMvc.perform(post("/barbers/me/appointments/{id}/no-show", id).header("Authorization", bearer(barberUser)));
    }

    private static long idOf(ResultActions result) throws Exception {
        return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id")).longValue();
    }
}
