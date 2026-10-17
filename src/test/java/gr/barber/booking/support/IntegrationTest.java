package gr.barber.booking.support;

import gr.barber.booking.model.Barber;
import gr.barber.booking.model.BarberStatus;
import gr.barber.booking.model.Role;
import gr.barber.booking.model.ServiceOffering;
import gr.barber.booking.model.User;
import gr.barber.booking.model.WorkingHours;
import gr.barber.booking.repository.BarberRepository;
import gr.barber.booking.repository.ServiceOfferingRepository;
import gr.barber.booking.repository.UserRepository;
import gr.barber.booking.repository.WorkingHoursRepository;
import gr.barber.booking.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;

/**
 * Base for tests that run the whole application against a real PostgreSQL in Docker.
 * All subclasses share one Spring context and one database container.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
public abstract class IntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected MutableClock clock;

    @Autowired
    protected JwtService jwtService;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected BarberRepository barberRepository;

    @Autowired
    protected ServiceOfferingRepository serviceOfferingRepository;

    @Autowired
    protected WorkingHoursRepository workingHoursRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // No real emails in tests; tests can verify what would have been sent.
    @MockitoBean
    protected JavaMailSender mailSender;

    @BeforeEach
    void resetState() {
        jdbcTemplate.execute("""
                TRUNCATE appointments, time_off, working_hours, service_offerings, shop_invitations, shops, barber_photos, barbers,
                         email_verification_tokens, password_reset_tokens, users RESTART IDENTITY CASCADE
                """);
        clock.set(TestcontainersConfiguration.NOW);
    }

    protected User createUser(String name, String email, Role role) {
        User user = new User(name, email, "6900000000", "not-used", role);
        user.setEmailVerified(true);
        return userRepository.save(user);
    }

    protected Barber createApprovedBarber(User user, boolean autoConfirm) {
        Barber barber = new Barber(user);
        barber.setCity("Athens");
        barber.setStatus(BarberStatus.APPROVED);
        barber.setAutoConfirmBookings(autoConfirm);
        return barberRepository.save(barber);
    }

    protected ServiceOffering createService(Barber barber, String name, int minutes, String price) {
        ServiceOffering service = new ServiceOffering(barber);
        service.setName(name);
        service.setDurationMinutes(minutes);
        service.setPrice(new BigDecimal(price));
        return serviceOfferingRepository.save(service);
    }

    protected void addShift(Barber barber, DayOfWeek day, String start, String end) {
        workingHoursRepository.save(new WorkingHours(barber, day, LocalTime.parse(start), LocalTime.parse(end)));
    }

    protected String bearer(User user) {
        return "Bearer " + jwtService.generateToken(user);
    }
}
