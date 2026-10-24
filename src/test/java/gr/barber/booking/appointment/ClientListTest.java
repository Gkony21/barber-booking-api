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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.DayOfWeek;
import java.time.Instant;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The barber's customers, worked out from their appointments. "Now" is Monday 7 January 2030, 10:00
 * Athens; the barber works Tuesdays and Wednesdays 10:00-18:00.
 */
class ClientListTest extends IntegrationTest {

    private User barberUser;
    private User maria;
    private Barber barber;
    private ServiceOffering haircut;
    private ServiceOffering beard;
    private ServiceOffering wash;

    @BeforeEach
    void setUp() {
        maria = createUser("Maria", "maria@test.gr", Role.CUSTOMER); // mobile 6900000000
        barberUser = createUser("Nikos", "nikos@test.gr", Role.BARBER);
        barber = createApprovedBarber(barberUser, true);
        haircut = createService(barber, "Haircut", 30, "12.00");
        beard = createService(barber, "Haircut + beard", 40, "15.00");
        wash = createService(barber, "Wash", 10, "2.00");
        wash.setKind(ServiceKind.EXTRA);
        serviceOfferingRepository.save(wash);
        addShift(barber, DayOfWeek.TUESDAY, "10:00", "18:00");
        addShift(barber, DayOfWeek.WEDNESDAY, "10:00", "18:00");
    }

    @Test
    void someoneWhoPhonedAndSomeoneWhoBookedInTheAppAreOneCustomerWhenTheNumberMatches() throws Exception {
        book(maria, "2030-01-08T10:00", haircut);
        manual("2030-01-09T11:00", "Μαρία Κ.", "+30 690 000 0000", beard);
        manual("2030-01-09T12:00", "Κώστας", "6911111111", haircut);

        clients(null)
                .andExpect(jsonPath("$", hasSize(2)))
                // Newest first; the name is the one used most recently.
                .andExpect(jsonPath("$[0].name").value("Κώστας"))
                .andExpect(jsonPath("$[1].key").value("p6900000000"))
                .andExpect(jsonPath("$[1].name").value("Μαρία Κ."))
                .andExpect(jsonPath("$[1].appCustomer").value(true))
                .andExpect(jsonPath("$[1].nextVisit").value("2030-01-08T10:00:00+02:00"))
                .andExpect(jsonPath("$[1].lastServiceId").value(beard.getId()));

        // The schedule says who each appointment is, to open their card.
        mockMvc.perform(get("/barbers/me/appointments").param("from", "2030-01-08").param("to", "2030-01-09")
                        .header("Authorization", bearer(barberUser)))
                .andExpect(jsonPath("$[0].clientKey").value("p6900000000"))
                .andExpect(jsonPath("$[1].clientKey").value("p6900000000"));
    }

    @Test
    void walkInsWithoutANumberAreKnownByNameAndSearchIgnoresAccents() throws Exception {
        manual("2030-01-08T10:00", "Κώστας Π.", null, haircut);
        manual("2030-01-09T10:00", "κωστας  π.", null, haircut);
        manual("2030-01-09T11:00", "Ελένη", "6922222222", haircut);

        clients(null).andExpect(jsonPath("$", hasSize(2)));
        clients("ΚΩΣΤΑ").andExpect(jsonPath("$[*].name", contains("κωστας  π.")));
        clients("2222").andExpect(jsonPath("$[*].name", contains("Ελένη")));
        clients("69").andExpect(jsonPath("$", hasSize(0))); // too few digits to search by number
    }

    @Test
    void theCardShowsVisitsNoShowsAndWhatTheyHadLastTime() throws Exception {
        long first = idOf(book(maria, "2030-01-08T10:00", haircut));
        mockMvc.perform(post("/appointments").header("Authorization", bearer(maria)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"barberId\":%d,\"serviceId\":%d,\"extraIds\":[%d],\"startTime\":\"2030-01-09T10:00\"}"
                        .formatted(barber.getId(), beard.getId(), wash.getId())));

        clock.set(Instant.parse("2030-01-09T12:00:00Z")); // Wednesday 14:00: both have taken place
        mockMvc.perform(post("/barbers/me/appointments/{id}/no-show", first).header("Authorization", bearer(barberUser)));

        mockMvc.perform(get("/barbers/me/clients/{key}", "p6900000000").header("Authorization", bearer(barberUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.client.visits").value(1))
                .andExpect(jsonPath("$.client.noShows").value(1))
                .andExpect(jsonPath("$.client.lastVisit").value("2030-01-09T10:00:00+02:00"))
                .andExpect(jsonPath("$.client.nextVisit").value(nullValue()))
                .andExpect(jsonPath("$.client.lastServiceId").value(beard.getId()))
                .andExpect(jsonPath("$.client.lastExtraIds", contains(wash.getId().intValue())))
                .andExpect(jsonPath("$.history", hasSize(2)))
                .andExpect(jsonPath("$.history[0].status").value("CONFIRMED"))
                .andExpect(jsonPath("$.history[1].status").value("NO_SHOW"));
    }

    @Test
    void notesAreTheBarbersOwnAndStayWithTheCustomer() throws Exception {
        book(maria, "2030-01-08T10:00", haircut);

        note("p6900000000", "νο 1 στο πλάι").andExpect(jsonPath("$.note").value("νο 1 στο πλάι"));
        clients(null).andExpect(jsonPath("$[0].note").value("νο 1 στο πλάι"));
        mockMvc.perform(get("/appointments/me").header("Authorization", bearer(maria)))
                .andExpect(jsonPath("$[0].note").value(nullValue()));

        note("p6900000000", "  ").andExpect(jsonPath("$.note").value(nullValue()));
        clients(null).andExpect(jsonPath("$[0].note").value(nullValue()));

        // Another barber's customers are not in this list.
        note("p6911111111", "who?").andExpect(status().isNotFound());
        User other = createUser("Giorgos", "giorgos@test.gr", Role.BARBER);
        createApprovedBarber(other, true);
        mockMvc.perform(get("/barbers/me/clients/{key}", "p6900000000").header("Authorization", bearer(other)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/barbers/me/clients").header("Authorization", bearer(maria)))
                .andExpect(status().isForbidden());
    }

    // ---- helpers ----

    private ResultActions clients(String query) throws Exception {
        var request = get("/barbers/me/clients").header("Authorization", bearer(barberUser));
        return mockMvc.perform(query == null ? request : request.param("q", query));
    }

    private ResultActions note(String key, String text) throws Exception {
        return mockMvc.perform(put("/barbers/me/clients/{key}/note", key).header("Authorization", bearer(barberUser))
                .contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"%s\"}".formatted(text)));
    }

    private ResultActions book(User who, String startTime, ServiceOffering service) throws Exception {
        return mockMvc.perform(post("/appointments").header("Authorization", bearer(who))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"barberId\":%d,\"serviceId\":%d,\"startTime\":\"%s\"}".formatted(barber.getId(), service.getId(), startTime)));
    }

    private void manual(String startTime, String name, String phone, ServiceOffering service) throws Exception {
        mockMvc.perform(post("/barbers/me/appointments").header("Authorization", bearer(barberUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"serviceId\":%d,\"startTime\":\"%s\",\"customerName\":\"%s\"%s}".formatted(service.getId(), startTime,
                                name, phone == null ? "" : ",\"customerPhone\":\"" + phone + "\"")))
                .andExpect(status().isCreated());
    }

    private static long idOf(ResultActions result) throws Exception {
        return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id")).longValue();
    }
}
