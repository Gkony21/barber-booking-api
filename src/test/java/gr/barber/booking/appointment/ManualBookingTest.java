package gr.barber.booking.appointment;

import gr.barber.booking.model.Barber;
import gr.barber.booking.model.Role;
import gr.barber.booking.model.ServiceOffering;
import gr.barber.booking.model.User;
import gr.barber.booking.notification.AppointmentReminderService;
import gr.barber.booking.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.test.web.servlet.ResultActions;

import java.time.DayOfWeek;
import java.time.Duration;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** "Now" is Monday 7 January 2030, 10:00 Athens; the barber works Tuesday 10:00–14:00. */
class ManualBookingTest extends IntegrationTest {

    @Autowired
    private AppointmentReminderService reminders;

    private User barberUser;
    private User customer;
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
    void barberEntersAPhoneBookingThatBlocksTheSlotForCustomers() throws Exception {
        manual("2030-01-08T11:00", "Κώστας", "6911111111")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.manual").value(true))
                .andExpect(jsonPath("$.customerName").value("Κώστας"))
                .andExpect(jsonPath("$.customerPhone").value("6911111111"))
                .andExpect(jsonPath("$.customerId").doesNotExist());

        mockMvc.perform(get("/barbers/{id}/availability", barber.getId())
                        .param("serviceId", haircut.getId().toString()).param("date", "2030-01-08"))
                .andExpect(jsonPath("$.startTimes", not(hasItem("11:00"))));

        mockMvc.perform(post("/appointments").header("Authorization", bearer(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"barberId\":%d,\"serviceId\":%d,\"startTime\":\"2030-01-08T11:00\"}"
                                .formatted(barber.getId(), haircut.getId())))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/barbers/me/appointments").header("Authorization", bearer(barberUser)))
                .andExpect(jsonPath("$[0].customerName").value("Κώστας"));

        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void barberMayBookOutsideTheirHoursButNotOverAnotherAppointment() throws Exception {
        manual("2030-01-08T20:45", "Late regular", null).andExpect(status().isCreated()); // after closing
        manual("2030-01-08T21:00", "Overlap", null).andExpect(status().isConflict());
    }

    @Test
    void refusesPastTimesUnknownServicesAndMissingNames() throws Exception {
        manual("2030-01-07T08:00", "Too late", null).andExpect(status().isBadRequest());
        mockMvc.perform(post("/barbers/me/appointments").header("Authorization", bearer(barberUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"serviceId\":9999,\"startTime\":\"2030-01-08T11:00\",\"customerName\":\"X\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/barbers/me/appointments").header("Authorization", bearer(barberUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"serviceId\":%d,\"startTime\":\"2030-01-08T11:00\",\"customerName\":\" \"}".formatted(haircut.getId())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void walkInsGetNoReminderEmails() throws Exception {
        manual("2030-01-08T11:00", "Κώστας", null).andExpect(status().isCreated());
        clock.advance(Duration.ofHours(23)); // 1 hour before

        reminders.sendDueReminders();

        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void cancellingAWalkInSendsNoEmail() throws Exception {
        String body = manual("2030-01-08T11:00", "Κώστας", null).andReturn().getResponse().getContentAsString();
        Integer id = com.jayway.jsonpath.JsonPath.read(body, "$.id");

        mockMvc.perform(post("/barbers/me/appointments/{id}/cancel", id).header("Authorization", bearer(barberUser)))
                .andExpect(status().isOk());

        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void customersCannotUseTheBarberEndpoint() throws Exception {
        mockMvc.perform(post("/barbers/me/appointments").header("Authorization", bearer(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"serviceId\":%d,\"startTime\":\"2030-01-08T11:00\",\"customerName\":\"X\"}".formatted(haircut.getId())))
                .andExpect(status().isForbidden());
    }

    private ResultActions manual(String start, String name, String phone) throws Exception {
        String phoneJson = phone == null ? "null" : "\"" + phone + "\"";
        return mockMvc.perform(post("/barbers/me/appointments")
                .header("Authorization", bearer(barberUser))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"serviceId\":%d,\"startTime\":\"%s\",\"customerName\":\"%s\",\"customerPhone\":%s}"
                        .formatted(haircut.getId(), start, name, phoneJson)));
    }
}
