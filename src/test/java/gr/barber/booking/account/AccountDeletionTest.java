package gr.barber.booking.account;

import gr.barber.booking.media.ImageStorage;
import gr.barber.booking.model.Appointment;
import gr.barber.booking.model.AppointmentStatus;
import gr.barber.booking.model.Barber;
import gr.barber.booking.model.BarberPhoto;
import gr.barber.booking.model.Role;
import gr.barber.booking.model.ServiceOffering;
import gr.barber.booking.model.User;
import gr.barber.booking.repository.AppointmentRepository;
import gr.barber.booking.repository.BarberPhotoRepository;
import gr.barber.booking.support.IntegrationTest;
import gr.barber.booking.support.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AccountDeletionTest extends IntegrationTest {

    private static final Instant NOW = TestcontainersConfiguration.NOW;

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private BarberPhotoRepository photoRepository;

    @Autowired
    private ImageStorage storage;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User customer;
    private User barberUser;
    private Barber barber;
    private ServiceOffering haircut;

    @BeforeEach
    void setUp() {
        customer = withPassword(createUser("Maria", "maria@test.gr", Role.CUSTOMER));
        barberUser = withPassword(createUser("Nikos", "nikos@test.gr", Role.BARBER));
        barber = createApprovedBarber(barberUser, true);
        haircut = createService(barber, "Haircut", 30, "12.00");
        Instant start = NOW.plus(Duration.ofDays(1));
        appointmentRepository.save(new Appointment(customer, barber, haircut, start, start.plus(Duration.ofMinutes(30)),
                AppointmentStatus.CONFIRMED, null));
    }

    @Test
    void wrongPasswordKeepsTheAccount() throws Exception {
        deleteAccount(customer, "not-my-password").andExpect(status().isBadRequest());
        assertThat(userRepository.findByEmail("maria@test.gr")).isPresent();
    }

    @Test
    void customerDeletionCancelsUpcomingBookingsAndTellsTheBarber() throws Exception {
        deleteAccount(customer, "correct-horse-1").andExpect(status().isNoContent());

        assertThat(userRepository.findByEmail("maria@test.gr")).isEmpty();
        assertThat(appointmentRepository.count()).isZero();
        assertThat(sentMails()).singleElement().satisfies(mail -> {
            assertThat(mail.getTo()).containsExactly("nikos@test.gr");
            assertThat(mail.getSubject()).startsWith("Ακύρωση ραντεβού");
        });
    }

    @Test
    void barberDeletionTellsCustomersAndRemovesProfileAndPhotos() throws Exception {
        String key = storage.save(new byte[]{1, 2, 3});
        photoRepository.save(new BarberPhoto(barber, key));

        deleteAccount(barberUser, "correct-horse-1").andExpect(status().isNoContent());

        assertThat(storage.load(key)).isEmpty();
        assertThat(barberRepository.count()).isZero();
        mockMvc.perform(get("/barbers")).andExpect(jsonPath("$").isEmpty());
        assertThat(sentMails()).singleElement().satisfies(mail -> {
            assertThat(mail.getTo()).containsExactly("maria@test.gr");
            assertThat(mail.getSubject()).isEqualTo("Το ραντεβού σου ακυρώθηκε");
        });
    }

    private User withPassword(User user) {
        user.setPassword(passwordEncoder.encode("correct-horse-1"));
        return userRepository.save(user);
    }

    private ResultActions deleteAccount(User who, String password) throws Exception {
        return mockMvc.perform(delete("/users/me")
                .header("Authorization", bearer(who))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"" + password + "\"}"));
    }

    private List<SimpleMailMessage> sentMails() {
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, atLeast(0)).send(captor.capture());
        return captor.getAllValues();
    }
}
