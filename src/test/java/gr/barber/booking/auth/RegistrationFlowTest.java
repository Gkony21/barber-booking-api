package gr.barber.booking.auth;

import gr.barber.booking.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RecordApplicationEvents
class RegistrationFlowTest extends IntegrationTest {

    @Autowired
    private ApplicationEvents events;

    @Autowired
    private UnverifiedAccountCleanup cleanup;

    @Autowired
    private AuthService authService;

    @Test
    void newAccountMustVerifyEmailBeforeLoggingIn() throws Exception {
        register("  Maria@Test.gr ", "CUSTOMER")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("maria@test.gr"));

        login("maria@test.gr", "pass1234")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));

        String token = events.stream(VerificationEmailRequested.class).findFirst().orElseThrow().token();

        verify(token).andExpect(status().isNoContent());
        verify(token).andExpect(status().isBadRequest()); // single use

        login("MARIA@test.gr", "pass1234")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void accountsNeverVerifiedAreRemovedAfterTwoDays() throws Exception {
        register("maria@test.gr", "CUSTOMER");
        register("eleni@test.gr", "CUSTOMER");
        verify(events.stream(VerificationEmailRequested.class).skip(1).findFirst().orElseThrow().token());

        clock.advance(Duration.ofHours(47));
        assertThat(cleanup.process()).isZero();
        clock.advance(Duration.ofHours(2));
        assertThat(cleanup.process()).isEqualTo(1);

        assertThat(userRepository.findByEmail("maria@test.gr")).isEmpty();
        assertThat(userRepository.findByEmail("eleni@test.gr")).isPresent();
        register("maria@test.gr", "CUSTOMER").andExpect(status().isOk()); // the address is free again
    }

    @Test
    void signUpsArePausedAfterTheDailyLimit() throws Exception {
        ReflectionTestUtils.setField(authService, "dailySignUps", 2);
        try {
            register("a@test.gr", "CUSTOMER").andExpect(status().isOk());
            register("b@test.gr", "BARBER").andExpect(status().isOk());
            register("c@test.gr", "CUSTOMER")
                    .andExpect(status().isTooManyRequests())
                    .andExpect(jsonPath("$.code").value("SIGN_UPS_PAUSED"));

            clock.advance(Duration.ofHours(25));
            register("c@test.gr", "CUSTOMER").andExpect(status().isOk());
        } finally {
            ReflectionTestUtils.setField(authService, "dailySignUps", 100_000);
        }
    }

    @Test
    void signUpRequiresAcceptingTheTerms() throws Exception {
        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"name":"Maria","email":"maria@test.gr","phoneNumber":"6900000000","password":"pass1234","role":"CUSTOMER"}"""))
                .andExpect(status().isBadRequest());

        register("maria@test.gr", "CUSTOMER").andExpect(status().isOk());
        assertThat(userRepository.findByEmail("maria@test.gr").orElseThrow().getTermsAcceptedAt()).isNotNull();
    }

    @Test
    void loginDoesNotRevealWhetherAnEmailIsRegistered() throws Exception {
        register("maria@test.gr", "CUSTOMER");

        String wrongPassword = login("maria@test.gr", "wrong-pass").andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        String unknownEmail = login("nobody@test.gr", "wrong-pass").andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        assertThat(wrongPassword).contains("Invalid email or password");
        assertThat(unknownEmail).contains("Invalid email or password");
    }

    @Test
    void registrationRejectsAdminRoleDuplicatesAndShortPasswords() throws Exception {
        register("x@test.gr", "ADMIN").andExpect(status().isBadRequest());
        register("maria@test.gr", "CUSTOMER").andExpect(status().isOk());
        register("MARIA@test.gr", "CUSTOMER").andExpect(status().isConflict());

        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"name":"A","email":"a@test.gr","phoneNumber":"6900000000","password":"short","role":"CUSTOMER","acceptTerms":true}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").exists());
    }

    @Test
    void resendIsSilentForUnknownEmailsAndRateLimited() throws Exception {
        register("maria@test.gr", "CUSTOMER");
        long before = events.stream(VerificationEmailRequested.class).count();

        resend("nobody@test.gr").andExpect(status().isAccepted());
        resend("maria@test.gr").andExpect(status().isAccepted()); // within cooldown: no new email

        assertThat(events.stream(VerificationEmailRequested.class).count()).isEqualTo(before);
    }

    private ResultActions register(String email, String role) throws Exception {
        return mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"name":"Maria","email":"%s","phoneNumber":"69 0000 0000","password":"pass1234","role":"%s","acceptTerms":true}""".formatted(email, role)));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"%s","password":"%s"}""".formatted(email, password)));
    }

    private ResultActions verify(String token) throws Exception {
        return mockMvc.perform(post("/auth/verify-email").contentType(MediaType.APPLICATION_JSON).content("""
                {"token":"%s"}""".formatted(token)));
    }

    private ResultActions resend(String email) throws Exception {
        return mockMvc.perform(post("/auth/resend-verification").contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"%s"}""".formatted(email)));
    }
}
