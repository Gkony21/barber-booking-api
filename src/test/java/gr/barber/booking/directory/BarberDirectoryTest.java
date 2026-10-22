package gr.barber.booking.directory;

import gr.barber.booking.model.Barber;
import gr.barber.booking.model.BarberPhoto;
import gr.barber.booking.model.Role;
import gr.barber.booking.repository.BarberPhotoRepository;
import gr.barber.booking.model.TimeOff;
import gr.barber.booking.repository.TimeOffRepository;
import gr.barber.booking.support.IntegrationTest;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.DayOfWeek;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/** "Now" is Monday 7 January 2030, 10:00 Athens. */
class BarberDirectoryTest extends IntegrationTest {

    @Autowired
    private BarberPhotoRepository photoRepository;

    @Autowired
    private TimeOffRepository timeOffRepository;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

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
        createService(tonight, "Wash", 10, "0.00"); // the card shows the usual service, not the cheapest
        addShift(tonight, DayOfWeek.MONDAY, "17:00", "21:00");
        photoRepository.save(new BarberPhoto(tonight, "11111111-1111-1111-1111-111111111111.jpg"));
        photoRepository.save(new BarberPhoto(tonight, "22222222-2222-2222-2222-222222222222.jpg"));
        tonight.setCoverKey("33333333-3333-3333-3333-333333333333.jpg");
        barberRepository.save(tonight);

        createApprovedBarber(createUser("New", "new@test.gr", Role.BARBER), true); // no services yet
    }

    @Test
    void listShowsCoverMainServiceAndNextFreeTimeSoonestFirst() throws Exception {
        mockMvc.perform(get("/barbers"))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].name").value("Nikos"))
                .andExpect(jsonPath("$[0].nextAvailable").value("2030-01-07T17:00:00+02:00"))
                .andExpect(jsonPath("$[0].featuredServiceName").value("Haircut"))
                .andExpect(jsonPath("$[0].featuredPrice").value(12.00))
                .andExpect(jsonPath("$[0].photoCount").value(2))
                // The barber's own cover, not the newest work photo.
                .andExpect(jsonPath("$[0].coverUrl").value("/media/33333333-3333-3333-3333-333333333333.jpg"))
                .andExpect(jsonPath("$[1].name").value("Giorgos"))
                .andExpect(jsonPath("$[1].nextAvailable").value("2030-01-08T10:00:00+02:00"))
                .andExpect(jsonPath("$[1].coverUrl").doesNotExist())
                .andExpect(jsonPath("$[?(@.name == 'New')]").isEmpty());
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

    // The next free time is searched only as far as the barber lets customers book.
    @Test
    void nextFreeTimeStaysWithinTheBarbersBookingWindow() throws Exception {
        // Off for the next three weeks: free again on Tuesday 29 January, 22 days from now.
        timeOffRepository.save(new TimeOff(tomorrow, LocalDate.parse("2030-01-07"), LocalDate.parse("2030-01-28"), null));

        mockMvc.perform(get("/barbers/{id}", tomorrow.getId())).andExpect(jsonPath("$.nextAvailable").doesNotExist());

        tomorrow.setBookingWindowDays(30);
        barberRepository.save(tomorrow);

        mockMvc.perform(get("/barbers/{id}", tomorrow.getId()))
                .andExpect(jsonPath("$.bookingWindowDays").value(30))
                .andExpect(jsonPath("$.nextAvailable").value("2030-01-29T10:00:00+02:00"));
    }

    // The list used to run dozens of queries per barber; now the count does not grow with them.
    @Test
    void listQueriesDoNotGrowWithTheNumberOfBarbers() throws Exception {
        for (int i = 0; i < 12; i++) {
            Barber barber = createApprovedBarber(createUser("Barber " + i, "b" + i + "@test.gr", Role.BARBER), true);
            barber.setBookingWindowDays(60);
            barberRepository.save(barber);
            createService(barber, "Haircut", 30, "10.00");
            addShift(barber, DayOfWeek.SUNDAY, "10:00", "11:00");
        }
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        mockMvc.perform(get("/barbers")).andExpect(jsonPath("$", hasSize(14)));

        assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(10);
    }

    // A barber without a main service or working hours cannot be booked, so browsing customers do
    // not see them; their own link still opens their page.
    @Test
    void barbersWhoCannotBeBookedAreLeftOutOfTheList() throws Exception {
        Barber noHours = createApprovedBarber(createUser("NoHours", "nohours@test.gr", Role.BARBER), true);
        createService(noHours, "Haircut", 30, "10.00");

        mockMvc.perform(get("/barbers"))
                .andExpect(jsonPath("$[?(@.name == 'NoHours')]").isEmpty())
                .andExpect(jsonPath("$[?(@.name == 'New')]").isEmpty());
        mockMvc.perform(get("/barbers/{id}", noHours.getId())).andExpect(jsonPath("$.name").value("NoHours"));
    }
}
