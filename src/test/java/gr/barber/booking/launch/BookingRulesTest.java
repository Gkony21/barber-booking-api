package gr.barber.booking.launch;

import gr.barber.booking.model.AppointmentStatus;
import gr.barber.booking.model.Barber;
import gr.barber.booking.model.Role;
import gr.barber.booking.model.ServiceOffering;
import gr.barber.booking.model.User;
import gr.barber.booking.notification.PendingRequestService;
import gr.barber.booking.repository.AppointmentRepository;
import gr.barber.booking.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.ResultActions;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
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

/** "Now" is Monday 7 January 2030, 10:00 Athens; the barber works Mondays and Tuesdays 10:00-20:00. */
class BookingRulesTest extends IntegrationTest {

    @Autowired
    private PendingRequestService pendingRequests;

    @Autowired
    private AppointmentRepository appointmentRepository;

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
        addShift(barber, DayOfWeek.MONDAY, "10:00", "20:00");
        addShift(barber, DayOfWeek.TUESDAY, "10:00", "20:00");
    }

    // ---- notice ----

    @Test
    void customersBookAtLeastTheBarbersNoticeAhead() throws Exception {
        availability("2030-01-07").andExpect(jsonPath("$.startTimes[0]").value("11:00")); // 1 hour by default
        book("2030-01-07T10:30").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("TOO_SOON"));

        updateProfile("{\"minNoticeMinutes\":30}").andExpect(jsonPath("$.minNoticeMinutes").value(30));
        availability("2030-01-07").andExpect(jsonPath("$.startTimes[0]").value("10:30"));
        book("2030-01-07T10:30").andExpect(status().isCreated());

        updateProfile("{\"minNoticeMinutes\":45}").andExpect(jsonPath("$.code").value("INVALID_MIN_NOTICE"));
    }

    // Requests expire 2 hours before they start, so they can only be sent 3 hours ahead.
    @Test
    void requestsNeedAtLeastThreeHoursSoTheBarberCanAnswer() throws Exception {
        barber.setAutoConfirmBookings(false);
        barberRepository.save(barber);

        availability("2030-01-07").andExpect(jsonPath("$.startTimes[0]").value("13:00"));
        book("2030-01-07T12:30").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("TOO_SOON"));
        book("2030-01-07T13:00").andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("PENDING"));
    }

    // ---- unanswered requests ----

    @Test
    void unansweredRequestsRemindTheBarberOnceThenExpireTwoHoursBefore() throws Exception {
        barber.setAutoConfirmBookings(false);
        barberRepository.save(barber);
        long id = idOf(book("2030-01-07T18:00"));
        clearInvocations(mailSender);

        clock.advance(Duration.ofMinutes(31));
        pendingRequests.process();
        pendingRequests.process();
        ArgumentCaptor<SimpleMailMessage> reminder = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(reminder.capture());
        assertThat(reminder.getValue().getTo()).containsExactly("nikos@test.gr");
        assertThat(reminder.getValue().getSubject()).startsWith("Αίτημα περιμένει απάντηση");
        clearInvocations(mailSender);

        clock.set(Instant.parse("2030-01-07T13:59:00Z")); // 15:59 Athens: still more than 2 hours before
        pendingRequests.process();
        verify(mailSender, never()).send(any(SimpleMailMessage.class));

        clock.set(Instant.parse("2030-01-07T14:00:00Z")); // 16:00 Athens: 2 hours before
        pendingRequests.process();
        assertThat(appointmentRepository.findById(id).orElseThrow().getStatus()).isEqualTo(AppointmentStatus.EXPIRED);
        ArgumentCaptor<SimpleMailMessage> mails = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, atLeastOnce()).send(mails.capture());
        assertThat(mails.getAllValues()).extracting(SimpleMailMessage::getSubject)
                .contains("Το αίτημά σου δεν επιβεβαιώθηκε");

        // The time is free again, and the request no longer counts towards the customer's two.
        assertThat(appointmentRepository.findActiveForBarberOverlapping(barber.getId(),
                Instant.parse("2030-01-07T16:00:00Z"), Instant.parse("2030-01-07T16:30:00Z"))).isEmpty();
        mockMvc.perform(get("/appointments/me").header("Authorization", bearer(customer)))
                .andExpect(jsonPath("$[0].status").value("EXPIRED"));
    }

    // ---- phone ----

    @Test
    void bookingNeedsAMobileNumberOnTheAccount() throws Exception {
        customer.setPhoneNumber(null);
        userRepository.save(customer);
        book("2030-01-08T10:00").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PHONE_REQUIRED"));

        mockMvc.perform(put("/users/me")
                        .header("Authorization", bearer(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Maria P.\",\"phoneNumber\":\"69 1234-5678\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Maria P."))
                .andExpect(jsonPath("$.phoneNumber").value("6912345678"));
        book("2030-01-08T10:00").andExpect(status().isCreated());
    }

    @Test
    void signUpNeedsAValidMobileNumber() throws Exception {
        register("").andExpect(jsonPath("$.code").value("PHONE_REQUIRED"));
        register("12345").andExpect(jsonPath("$.code").value("INVALID_PHONE"));
        register("+30 691 234 5678").andExpect(status().isOk()).andExpect(jsonPath("$.phoneNumber").value("+306912345678"));
    }

    // ---- account ----

    @Test
    void changingThePasswordNeedsTheCurrentOneAndSignsOutOtherDevices() throws Exception {
        customer.setPassword(passwordEncoderHash("old-secret-42"));
        userRepository.save(customer);
        String otherDevice = bearer(customer);

        changePassword(otherDevice, "wrong-one", "brand-new-secret").andExpect(jsonPath("$.code").value("WRONG_PASSWORD"));
        String token = JsonPath.read(changePassword(otherDevice, "old-secret-42", "brand-new-secret")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.token");

        mockMvc.perform(get("/users/me").header("Authorization", otherDevice)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/users/me").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.email").value("maria@test.gr"));
    }

    // ---- admin ----

    @Test
    void adminsHearAboutNewBarbers() throws Exception {
        createUser("Admin", "admin@test.gr", Role.ADMIN);
        User newBarber = createUser("Kostas", "kostas@test.gr", Role.BARBER);

        mockMvc.perform(post("/barbers/me")
                        .header("Authorization", bearer(newBarber))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"city\":\"Σπάρτη\"}"))
                .andExpect(status().isCreated());

        ArgumentCaptor<SimpleMailMessage> mail = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(mail.capture());
        assertThat(mail.getValue().getTo()).containsExactly("admin@test.gr");
        assertThat(mail.getValue().getText()).contains("Όνομα: Kostas", "Πόλη: Σπάρτη", "/admin");
    }

    // ---- helpers ----

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String passwordEncoderHash(String raw) {
        return passwordEncoder.encode(raw);
    }

    private ResultActions availability(String date) throws Exception {
        return mockMvc.perform(get("/barbers/{id}/availability", barber.getId())
                .param("serviceId", haircut.getId().toString()).param("date", date));
    }

    private ResultActions book(String startTime) throws Exception {
        return mockMvc.perform(post("/appointments")
                .header("Authorization", bearer(customer))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"barberId":%d,"serviceId":%d,"startTime":"%s"}""".formatted(barber.getId(), haircut.getId(), startTime)));
    }

    private ResultActions updateProfile(String json) throws Exception {
        return mockMvc.perform(put("/barbers/me")
                .header("Authorization", bearer(barberUser)).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions register(String phone) throws Exception {
        return mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"name":"New","email":"new@test.gr","phoneNumber":"%s","password":"brand-new-secret","role":"CUSTOMER","acceptTerms":true}"""
                .formatted(phone)));
    }

    private ResultActions changePassword(String token, String current, String next) throws Exception {
        return mockMvc.perform(post("/users/me/password")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"%s\",\"newPassword\":\"%s\"}".formatted(current, next)));
    }

    private static long idOf(ResultActions result) throws Exception {
        return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id")).longValue();
    }
}
