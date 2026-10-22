package gr.barber.booking.appointment;

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
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A customer picks one main service (or package) and any number of extras; lengths and prices
 * add up. "Now" is Monday 7 January 2030, 10:00 Athens; the barber works Tuesday 10:00-12:00.
 */
class ServiceExtrasTest extends IntegrationTest {

    private static final String TUESDAY = "2030-01-08";

    private User customer;
    private User barberUser;
    private Barber barber;
    private ServiceOffering haircut;
    private ServiceOffering wash;
    private ServiceOffering design;

    @BeforeEach
    void setUp() {
        customer = createUser("Maria", "maria@test.gr", Role.CUSTOMER);
        barberUser = createUser("Nikos", "nikos@test.gr", Role.BARBER);
        barber = createApprovedBarber(barberUser, true);
        haircut = createService(barber, "Κούρεμα", 30, "12.00");
        wash = extra(createService(barber, "Λούσιμο", 15, "0.00"));
        design = extra(createService(barber, "Σχέδια", 15, "5.00"));
        addShift(barber, DayOfWeek.TUESDAY, "10:00", "12:00");
    }

    @Test
    void extrasAddTheirTimeAndPriceToTheAppointment() throws Exception {
        // 60 minutes in a 10:00-12:00 shift with 30-minute starts: 10:00, 10:30, 11:00.
        mockMvc.perform(get("/barbers/{id}/availability", barber.getId())
                        .param("serviceId", haircut.getId().toString())
                        .param("extraIds", wash.getId() + "," + design.getId())
                        .param("date", TUESDAY))
                .andExpect(jsonPath("$.durationMinutes").value(60))
                .andExpect(jsonPath("$.startTimes", hasSize(3)))
                .andExpect(jsonPath("$.startTimes[2]").value("11:00"));

        book(TUESDAY + "T11:00", haircut.getId(), wash.getId(), design.getId())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.serviceName").value("Κούρεμα"))
                .andExpect(jsonPath("$.extras[0].name").value("Λούσιμο"))
                .andExpect(jsonPath("$.extras[1].name").value("Σχέδια"))
                .andExpect(jsonPath("$.durationMinutes").value(60))
                .andExpect(jsonPath("$.price").value(17.00))
                .andExpect(jsonPath("$.endTime").value("2030-01-08T12:00:00+02:00"));

        // Emails list the extras under the main service.
        ArgumentCaptor<SimpleMailMessage> mails = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, atLeastOnce()).send(mails.capture());
        assertThat(mails.getAllValues().getFirst().getText()).contains("Υπηρεσία: Κούρεμα", "Έξτρα: Λούσιμο, Σχέδια", "17,00 €");
    }

    @Test
    void theWholeLengthMustFitInTheShift() throws Exception {
        book(TUESDAY + "T11:30", haircut.getId(), wash.getId())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BARBER_NOT_WORKING"));
    }

    @Test
    void onlyTheBarbersOwnActiveExtrasCanBeAdded() throws Exception {
        // An extra is not a main service, and a main service is not an extra.
        book(TUESDAY + "T10:00", wash.getId()).andExpect(status().isNotFound());
        book(TUESDAY + "T10:00", haircut.getId(), haircut.getId())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_EXTRA"));
        book(TUESDAY + "T10:00", haircut.getId(), wash.getId(), wash.getId())
                .andExpect(status().isBadRequest());

        Barber other = createApprovedBarber(createUser("Kostas", "kostas@test.gr", Role.BARBER), true);
        ServiceOffering othersExtra = extra(createService(other, "Μασάζ", 10, "3.00"));
        book(TUESDAY + "T10:00", haircut.getId(), othersExtra.getId()).andExpect(status().isBadRequest());
    }

    // An extra is never the service on the card, even if it was added first.
    @Test
    void listingUsesMainServicesOnly() throws Exception {
        extra(createService(barber, "Μασάζ", 10, "3.00"));

        mockMvc.perform(get("/barbers/{id}", barber.getId()))
                .andExpect(jsonPath("$.featuredServiceName").value(haircut.getName()))
                .andExpect(jsonPath("$.featuredPrice").value(12.00));
    }

    // By default the first main service (usually the plain haircut), not the cheapest, e.g. a kids' cut.
    @Test
    void barbersChooseTheServiceOnTheirCard() throws Exception {
        ServiceOffering beard = createService(barber, "Κούρεμα + μούσι", 40, "15.00");
        createService(barber, "Παιδικό", 30, "8.00");
        mockMvc.perform(get("/barbers/{id}", barber.getId())).andExpect(jsonPath("$.featuredServiceName").value("Κούρεμα"));

        feature(beard.getId()).andExpect(status().isNoContent());
        mockMvc.perform(get("/barbers/{id}", barber.getId()))
                .andExpect(jsonPath("$.featuredServiceName").value("Κούρεμα + μούσι"))
                .andExpect(jsonPath("$.featuredPrice").value(15.00));
        mockMvc.perform(get("/barbers/{id}/services", barber.getId()))
                .andExpect(jsonPath("$[?(@.featured == true)].name").value(contains("Κούρεμα + μούσι")));

        feature(haircut.getId()).andExpect(status().isNoContent()); // replaces the earlier choice
        mockMvc.perform(get("/barbers/me/services").header("Authorization", bearer(barberUser)))
                .andExpect(jsonPath("$[?(@.featured == true)].name").value(contains("Κούρεμα")));
        feature(wash.getId()).andExpect(jsonPath("$.code").value("NOT_A_MAIN_SERVICE"));

        // A hidden service is never on the card.
        mockMvc.perform(post("/barbers/me/services/{id}/hide", haircut.getId()).header("Authorization", bearer(barberUser)));
        mockMvc.perform(get("/barbers/{id}", barber.getId())).andExpect(jsonPath("$.featuredServiceName").value("Κούρεμα + μούσι"));
    }

    private ResultActions feature(Long serviceId) throws Exception {
        return mockMvc.perform(post("/barbers/me/services/{id}/feature", serviceId).header("Authorization", bearer(barberUser)));
    }

    @Test
    void barbersMarkServicesAsExtrasAndEnterThemByHand() throws Exception {
        mockMvc.perform(put("/barbers/me/services/{id}", haircut.getId())
                        .header("Authorization", bearer(barberUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Κούρεμα","durationMinutes":30,"price":12.00}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("MAIN")); // left out: kind unchanged

        mockMvc.perform(post("/barbers/me/services")
                        .header("Authorization", bearer(barberUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Χρώμα","durationMinutes":20,"price":8.00,"kind":"EXTRA"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.kind").value("EXTRA"));

        mockMvc.perform(post("/barbers/me/appointments")
                        .header("Authorization", bearer(barberUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"serviceId":%d,"extraIds":[%d],"startTime":"%sT10:00","customerName":"Κώστας"}"""
                                .formatted(haircut.getId(), design.getId(), TUESDAY)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.extras[0].name").value("Σχέδια"))
                .andExpect(jsonPath("$.durationMinutes").value(45));
    }

    private ServiceOffering extra(ServiceOffering service) {
        service.setKind(ServiceKind.EXTRA);
        return serviceOfferingRepository.save(service);
    }

    private ResultActions book(String startTime, Long serviceId, Long... extraIds) throws Exception {
        String extras = java.util.Arrays.stream(extraIds).map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
        return mockMvc.perform(post("/appointments")
                .header("Authorization", bearer(customer))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"barberId":%d,"serviceId":%d,"extraIds":[%s],"startTime":"%s"}"""
                        .formatted(barber.getId(), serviceId, extras, startTime)));
    }
}
