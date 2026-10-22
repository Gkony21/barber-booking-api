package gr.barber.booking.appointment;

import com.jayway.jsonpath.JsonPath;
import gr.barber.booking.model.Barber;
import gr.barber.booking.model.Role;
import gr.barber.booking.model.ServiceOffering;
import gr.barber.booking.model.User;
import gr.barber.booking.notification.PendingRequestService;
import gr.barber.booking.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.test.web.servlet.ResultActions;

import java.time.DayOfWeek;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A customer moving a confirmed appointment of a barber who approves by hand: the appointment keeps
 * its time until the barber accepts the new one. "Now" is Monday 7 January 2030, 10:00 Athens; the
 * barber works Tuesdays 10:00-18:00 with 15-minute starts.
 */
class ChangeRequestTest extends IntegrationTest {

    private static final String TUESDAY = "2030-01-08";

    @Autowired
    private PendingRequestService pendingRequests;

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
        barber = createApprovedBarber(barberUser, false);
        barber.setSlotStepMinutes(15);
        barberRepository.save(barber);
        haircut = createService(barber, "Haircut", 30, "12.00");
        addShift(barber, DayOfWeek.TUESDAY, "10:00", "18:00");
    }

    @Test
    void theAppointmentKeepsItsTimeUntilTheBarberAcceptsTheNewOne() throws Exception {
        long id = bookAndConfirm(TUESDAY + "T10:00");
        clearInvocations(mailSender);

        reschedule(id, TUESDAY + "T15:00")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.startTime").value("2030-01-08T10:00:00+02:00"))
                .andExpect(jsonPath("$.requestedStartTime").value("2030-01-08T15:00:00+02:00"));
        assertThat(subjects()).contains("Η αλλαγή ώρας στάλθηκε", "Αίτημα αλλαγής ώρας: Τρίτη 8 Ιανουαρίου 2030, 15:00");

        // The customer sees one appointment with the change on it; the barber sees the change as a request.
        mine().andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].requestedStartTime").value("2030-01-08T15:00:00+02:00"));
        schedule().andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].requestedStartTime").value("2030-01-08T15:00:00+02:00"))
                .andExpect(jsonPath("$[1].status").value("PENDING"))
                .andExpect(jsonPath("$[1].originalStartTime").value("2030-01-08T10:00:00+02:00"));

        // Both times stay taken meanwhile, so nobody else can book either of them.
        availability().andExpect(jsonPath("$.startTimes", not(hasItem("10:00"))))
                .andExpect(jsonPath("$.startTimes", not(hasItem("15:00"))));

        // Asking again moves the change rather than adding another.
        reschedule(id, TUESDAY + "T16:00").andExpect(jsonPath("$.requestedStartTime").value("2030-01-08T16:00:00+02:00"));
        schedule().andExpect(jsonPath("$", hasSize(2)));
        long change = changeId();
        clearInvocations(mailSender);

        barberAction(change, "confirm")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.startTime").value("2030-01-08T16:00:00+02:00"))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
        assertThat(subjects()).contains("Η αλλαγή ώρας έγινε δεκτή");
        mine().andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].startTime").value("2030-01-08T16:00:00+02:00"))
                .andExpect(jsonPath("$[0].requestedStartTime").value(nullValue()));
        schedule().andExpect(jsonPath("$", hasSize(1)));
        availability().andExpect(jsonPath("$.startTimes", hasItems("10:00", "15:00")));
    }

    @Test
    void aDeclinedChangeLeavesTheAppointmentWhereItWas() throws Exception {
        long id = bookAndConfirm(TUESDAY + "T10:00");
        reschedule(id, TUESDAY + "T15:00");
        clearInvocations(mailSender);

        barberAction(changeId(), "reject")
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.startTime").value("2030-01-08T10:00:00+02:00"))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
        assertThat(subjects()).contains("Η αλλαγή ώρας δεν έγινε δεκτή");
        mine().andExpect(jsonPath("$[0].requestedStartTime").value(nullValue()));
        availability().andExpect(jsonPath("$.startTimes", hasItem("15:00")));
    }

    @Test
    void theAppointmentsOwnTimeCountsAsFreeWhenMovingIt() throws Exception {
        long id = bookAndConfirm(TUESDAY + "T10:00");

        // "A quarter of an hour later" overlaps the appointment itself, which is fine.
        mockMvc.perform(get("/appointments/{id}/availability", id).param("date", TUESDAY).header("Authorization", bearer(customer)))
                .andExpect(jsonPath("$.durationMinutes").value(30))
                .andExpect(jsonPath("$.startTimes", hasItems("10:00", "10:15")));
        mockMvc.perform(get("/appointments/{id}/days", id).param("from", TUESDAY).param("to", TUESDAY)
                        .header("Authorization", bearer(customer)))
                .andExpect(jsonPath("$[0].status").value("OPEN"));
        mockMvc.perform(get("/appointments/{id}/availability", id).param("date", TUESDAY).header("Authorization", bearer(other)))
                .andExpect(status().isNotFound());

        reschedule(id, TUESDAY + "T10:15").andExpect(jsonPath("$.requestedStartTime").value("2030-01-08T10:15:00+02:00"));
        barberAction(changeId(), "confirm").andExpect(jsonPath("$.startTime").value("2030-01-08T10:15:00+02:00"));

        // Picking the time it already has asks nothing.
        reschedule(id, TUESDAY + "T10:15").andExpect(jsonPath("$.requestedStartTime").value(nullValue()));
        schedule().andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void anUnansweredChangeClosesBeforeTheEarlierOfTheTwoTimes() throws Exception {
        long id = bookAndConfirm(TUESDAY + "T10:00");
        reschedule(id, TUESDAY + "T16:00");
        clearInvocations(mailSender);

        clock.set(Instant.parse("2030-01-08T06:05:00Z")); // Tuesday 08:05 Athens: the appointment is under 2 hours away
        pendingRequests.process();

        assertThat(subjects()).contains("Η αλλαγή ώρας δεν απαντήθηκε", "Αίτημα αλλαγής έκλεισε χωρίς απάντηση");
        mine().andExpect(jsonPath("$[0].status").value("CONFIRMED"))
                .andExpect(jsonPath("$[0].startTime").value("2030-01-08T10:00:00+02:00"))
                .andExpect(jsonPath("$[0].requestedStartTime").value(nullValue()));
        schedule().andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void customersWithdrawAChangeOrCancelTheWholeAppointment() throws Exception {
        long id = bookAndConfirm(TUESDAY + "T10:00");
        reschedule(id, TUESDAY + "T15:00");
        clearInvocations(mailSender);

        customerAction(id, "withdraw-change").andExpect(status().isOk())
                .andExpect(jsonPath("$.requestedStartTime").value(nullValue()));
        assertThat(subjects()).contains("Η αλλαγή ώρας ακυρώθηκε");
        customerAction(id, "withdraw-change").andExpect(jsonPath("$.code").value("NO_CHANGE"));

        reschedule(id, TUESDAY + "T15:00");
        customerAction(id, "cancel").andExpect(jsonPath("$.status").value("CANCELLED"));
        schedule().andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].status").value("CANCELLED"));
        availability().andExpect(jsonPath("$.startTimes", hasItems("10:00", "15:00")));
    }

    @Test
    void aBarberCancellingTheAppointmentAlsoClosesItsChange() throws Exception {
        long id = bookAndConfirm(TUESDAY + "T10:00");
        reschedule(id, TUESDAY + "T15:00");

        barberAction(id, "cancel").andExpect(jsonPath("$.status").value("CANCELLED"));
        schedule().andExpect(jsonPath("$", hasSize(1)));
        availability().andExpect(jsonPath("$.startTimes", hasItem("15:00")));
    }

    @Test
    void aChangeDoesNotCountAsAnotherBooking() throws Exception {
        long id = bookAndConfirm(TUESDAY + "T10:00");
        reschedule(id, TUESDAY + "T15:00");

        book(customer, TUESDAY + "T12:00").andExpect(status().isCreated());
        book(customer, TUESDAY + "T13:00").andExpect(jsonPath("$.code").value("BOOKING_LIMIT"));
        // The other booking is separate: the appointment cannot move on top of it.
        reschedule(id, TUESDAY + "T12:00").andExpect(jsonPath("$.code").value("SLOT_TAKEN"));
    }

    // ---- helpers ----

    private long bookAndConfirm(String startTime) throws Exception {
        long id = idOf(book(customer, startTime).andExpect(status().isCreated()));
        barberAction(id, "confirm").andExpect(status().isOk());
        return id;
    }

    private long changeId() throws Exception {
        String body = schedule().andReturn().getResponse().getContentAsString();
        List<Number> ids = JsonPath.read(body, "$[?(@.originalStartTime != null)].id");
        assertThat(ids).hasSize(1);
        return ids.getFirst().longValue();
    }

    private List<String> subjects() {
        ArgumentCaptor<SimpleMailMessage> mails = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, atLeastOnce()).send(mails.capture());
        return mails.getAllValues().stream().map(SimpleMailMessage::getSubject).toList();
    }

    private ResultActions book(User who, String startTime) throws Exception {
        return mockMvc.perform(post("/appointments").header("Authorization", bearer(who))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"barberId\":%d,\"serviceId\":%d,\"startTime\":\"%s\"}".formatted(barber.getId(), haircut.getId(), startTime)));
    }

    private ResultActions reschedule(long id, String startTime) throws Exception {
        return mockMvc.perform(post("/appointments/{id}/reschedule", id).header("Authorization", bearer(customer))
                .contentType(MediaType.APPLICATION_JSON).content("{\"startTime\":\"%s\"}".formatted(startTime)));
    }

    private ResultActions customerAction(long id, String action) throws Exception {
        return mockMvc.perform(post("/appointments/{id}/" + action, id).header("Authorization", bearer(customer)));
    }

    private ResultActions barberAction(long id, String action) throws Exception {
        return mockMvc.perform(post("/barbers/me/appointments/{id}/" + action, id).header("Authorization", bearer(barberUser)));
    }

    private ResultActions mine() throws Exception {
        return mockMvc.perform(get("/appointments/me").header("Authorization", bearer(customer)));
    }

    private ResultActions schedule() throws Exception {
        return mockMvc.perform(get("/barbers/me/appointments").param("from", TUESDAY).param("to", TUESDAY)
                .header("Authorization", bearer(barberUser)));
    }

    private ResultActions availability() throws Exception {
        return mockMvc.perform(get("/barbers/{id}/availability", barber.getId())
                .param("serviceId", haircut.getId().toString()).param("date", TUESDAY));
    }

    private static long idOf(ResultActions result) throws Exception {
        return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id")).longValue();
    }
}
