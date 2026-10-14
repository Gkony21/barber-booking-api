package gr.barber.booking.auth;

import gr.barber.booking.model.Role;
import gr.barber.booking.model.User;
import gr.barber.booking.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RecordApplicationEvents
class PasswordTest extends IntegrationTest {

    @Autowired
    private ApplicationEvents events;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User maria;

    @BeforeEach
    void setUp() {
        maria = createUser("Maria", "maria@test.gr", Role.CUSTOMER);
        maria.setPassword(passwordEncoder.encode("old-secret-42"));
        userRepository.save(maria);
    }

    // ---- policy ----

    @Test
    void registrationRefusesCommonAndGuessablePasswords() throws Exception {
        for (String weak : new String[]{"12345678", "Password1", "olympiakos7", "aaaaaaaa", "abcdefgh", "98765432", "new.user@test.gr"}) {
            register("new.user@test.gr", weak)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("WEAK_PASSWORD"));
        }
        register("new.user@test.gr", "fade-at-nine-thirty").andExpect(status().isOk());
    }

    // ---- reset ----

    @Test
    void forgotPasswordEmailsALinkThatSetsANewPasswordOnce() throws Exception {
        forgot("Maria@Test.gr").andExpect(status().isAccepted());
        String token = events.stream(PasswordResetRequested.class).findFirst().orElseThrow().token();

        reset(token, "12345678").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("WEAK_PASSWORD"));
        reset(token, "brand-new-secret").andExpect(status().isNoContent());
        reset(token, "another-new-secret").andExpect(status().isBadRequest()); // single use

        login("maria@test.gr", "old-secret-42").andExpect(status().isUnauthorized());
        login("maria@test.gr", "brand-new-secret").andExpect(status().isOk());
    }

    @Test
    void resetLinksExpireAfterAnHour() throws Exception {
        forgot("maria@test.gr");
        String token = events.stream(PasswordResetRequested.class).findFirst().orElseThrow().token();

        clock.advance(Duration.ofMinutes(61));

        reset(token, "brand-new-secret").andExpect(status().isBadRequest());
    }

    @Test
    void resettingAlsoVerifiesTheEmail() throws Exception {
        maria.setEmailVerified(false);
        userRepository.save(maria);
        forgot("maria@test.gr");
        String token = events.stream(PasswordResetRequested.class).findFirst().orElseThrow().token();

        reset(token, "brand-new-secret").andExpect(status().isNoContent());

        assertThat(userRepository.findByEmail("maria@test.gr").orElseThrow().isEmailVerified()).isTrue();
    }

    @Test
    void unknownEmailsGetTheSameAnswerAndNoEmail() throws Exception {
        forgot("nobody@test.gr").andExpect(status().isAccepted());
        forgot("maria@test.gr").andExpect(status().isAccepted());
        forgot("maria@test.gr").andExpect(status().isAccepted()); // within cooldown: no second email

        assertThat(events.stream(PasswordResetRequested.class).count()).isEqualTo(1);
    }

    // ---- helpers ----

    private ResultActions register(String email, String password) throws Exception {
        return mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"name":"New","email":"%s","password":"%s","role":"CUSTOMER","acceptTerms":true}""".formatted(email, password)));
    }

    private ResultActions forgot(String email) throws Exception {
        return mockMvc.perform(post("/auth/forgot-password").contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"%s"}""".formatted(email)));
    }

    private ResultActions reset(String token, String password) throws Exception {
        return mockMvc.perform(post("/auth/reset-password").contentType(MediaType.APPLICATION_JSON).content("""
                {"token":"%s","password":"%s"}""".formatted(token, password)));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"%s","password":"%s"}""".formatted(email, password)));
    }
}
