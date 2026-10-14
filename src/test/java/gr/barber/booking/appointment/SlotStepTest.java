package gr.barber.booking.appointment;

import gr.barber.booking.model.Barber;
import gr.barber.booking.model.Role;
import gr.barber.booking.model.ServiceOffering;
import gr.barber.booking.model.User;
import gr.barber.booking.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.DayOfWeek;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** "Now" is Monday 7 January 2030, 10:00 Athens; the barber works Tuesday 10:00–12:00. */
class SlotStepTest extends IntegrationTest {

    private static final String TUESDAY = "2030-01-08";

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
        addShift(barber, DayOfWeek.TUESDAY, "10:00", "12:00");
    }

    @Test
    void newBarbersOfferHalfHourStartsByDefault() throws Exception {
        availability().andExpect(jsonPath("$.startTimes", contains("10:00", "10:30", "11:00", "11:30")));
    }

    @Test
    void barberCanSwitchToQuarterHourStarts() throws Exception {
        mockMvc.perform(put("/barbers/me")
                        .header("Authorization", bearer(barberUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slotStepMinutes\":15}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slotStepMinutes").value(15));

        availability().andExpect(jsonPath("$.startTimes", contains(
                "10:00", "10:15", "10:30", "10:45", "11:00", "11:15", "11:30")));
    }

    @Test
    void onlyFifteenOrThirtyMinutesAreAccepted() throws Exception {
        mockMvc.perform(put("/barbers/me")
                        .header("Authorization", bearer(barberUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slotStepMinutes\":20}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void bookingsMustStartOnTheBarbersStep() throws Exception {
        book("10:15").andExpect(status().isBadRequest());
        book("10:30").andExpect(status().isCreated());
    }

    private ResultActions availability() throws Exception {
        return mockMvc.perform(get("/barbers/{id}/availability", barber.getId())
                .param("serviceId", haircut.getId().toString())
                .param("date", TUESDAY));
    }

    private ResultActions book(String time) throws Exception {
        return mockMvc.perform(post("/appointments")
                .header("Authorization", bearer(customer))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"barberId":%d,"serviceId":%d,"startTime":"%sT%s"}""".formatted(barber.getId(), haircut.getId(), TUESDAY, time)));
    }
}
