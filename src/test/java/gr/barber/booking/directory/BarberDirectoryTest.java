package gr.barber.booking.directory;

import gr.barber.booking.model.Barber;
import gr.barber.booking.model.BarberPhoto;
import gr.barber.booking.model.Role;
import gr.barber.booking.repository.BarberPhotoRepository;
import gr.barber.booking.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.DayOfWeek;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/** "Now" is Monday 7 January 2030, 10:00 Athens. */
class BarberDirectoryTest extends IntegrationTest {

    @Autowired
    private BarberPhotoRepository photoRepository;

    private Barber tomorrow;
    private Barber tonight;

    @BeforeEach
    void setUp() {
        tomorrow = createApprovedBarber(createUser("Giorgos", "giorgos@test.gr", Role.BARBER), true);
        createService(tomorrow, "Haircut", 30, "14.00");
        addShift(tomorrow, DayOfWeek.TUESDAY, "10:00", "14:00");

        tonight = createApprovedBarber(createUser("Nikos", "nikos@test.gr", Role.BARBER), true);
        createService(tonight, "Haircut", 30, "12.00");
        createService(tonight, "Beard", 20, "8.00");
        addShift(tonight, DayOfWeek.MONDAY, "17:00", "21:00");
        photoRepository.save(new BarberPhoto(tonight, "11111111-1111-1111-1111-111111111111.jpg"));
        photoRepository.save(new BarberPhoto(tonight, "22222222-2222-2222-2222-222222222222.jpg"));

        createApprovedBarber(createUser("New", "new@test.gr", Role.BARBER), true); // no services yet
    }

    @Test
    void listShowsCoverStartingPriceAndNextFreeTimeSoonestFirst() throws Exception {
        mockMvc.perform(get("/barbers"))
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].name").value("Nikos"))
                .andExpect(jsonPath("$[0].nextAvailable").value("2030-01-07T17:00:00+02:00"))
                .andExpect(jsonPath("$[0].fromPrice").value(8.00))
                .andExpect(jsonPath("$[0].photoCount").value(2))
                .andExpect(jsonPath("$[0].coverUrl").value("/media/22222222-2222-2222-2222-222222222222.jpg"))
                .andExpect(jsonPath("$[1].name").value("Giorgos"))
                .andExpect(jsonPath("$[1].nextAvailable").value("2030-01-08T10:00:00+02:00"))
                .andExpect(jsonPath("$[1].coverUrl").doesNotExist())
                .andExpect(jsonPath("$[2].name").value("New"))
                .andExpect(jsonPath("$[2].nextAvailable").doesNotExist());
    }

    @Test
    void availableTodayKeepsOnlyBarbersWithAFreeTimeLaterToday() throws Exception {
        mockMvc.perform(get("/barbers").param("availableToday", "true"))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("Nikos"));
    }

    @Test
    void profileCarriesTheSameListingDetails() throws Exception {
        mockMvc.perform(get("/barbers/{id}", tonight.getId()))
                .andExpect(jsonPath("$.nextAvailable").value("2030-01-07T17:00:00+02:00"))
                .andExpect(jsonPath("$.photoCount").value(2));
    }
}
