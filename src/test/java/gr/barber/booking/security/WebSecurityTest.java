package gr.barber.booking.security;

import gr.barber.booking.model.Role;
import gr.barber.booking.model.User;
import gr.barber.booking.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WebSecurityTest extends IntegrationTest {

    @Test
    void webAppOriginMayCallTheApi() throws Exception {
        mockMvc.perform(options("/appointments")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "authorization,content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
                .andExpect(header().string("Access-Control-Allow-Methods", containsString("POST")));
    }

    @Test
    void otherOriginsAreRefused() throws Exception {
        mockMvc.perform(options("/appointments")
                        .header("Origin", "https://evil.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }

    @Test
    void missingOrInvalidTokenIsUnauthorizedNotForbidden() throws Exception {
        mockMvc.perform(get("/appointments/me")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/appointments/me").header("Authorization", "Bearer not-a-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokensOfDeletedAccountsStopWorking() throws Exception {
        User maria = createUser("Maria", "maria@test.gr", Role.CUSTOMER);
        String token = bearer(maria);
        mockMvc.perform(get("/appointments/me").header("Authorization", token)).andExpect(status().isOk());

        userRepository.delete(maria);

        mockMvc.perform(get("/appointments/me").header("Authorization", token)).andExpect(status().isUnauthorized());
    }

    // The role comes from the database, not from the token, so it cannot be stale or forged.
    @Test
    void roleIsTakenFromTheAccountNotTheToken() throws Exception {
        User maria = createUser("Maria", "maria@test.gr", Role.CUSTOMER);
        String customerToken = bearer(maria);

        maria.setRole(Role.ADMIN);
        userRepository.save(maria);

        mockMvc.perform(get("/admin/barbers").header("Authorization", customerToken)).andExpect(status().isOk());
    }

    // These let a stolen token change the email and password or delete the account without the
    // current password; account changes go through /users/me and the reset-password flow instead.
    @Test
    void accountsCannotBeChangedOrDeletedById() throws Exception {
        User maria = createUser("Maria", "maria@test.gr", Role.CUSTOMER);

        mockMvc.perform(put("/users/{id}", maria.getId())
                        .header("Authorization", bearer(maria))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"X","email":"thief@test.gr","password":"stolen-password-1","role":"CUSTOMER"}"""))
                .andExpect(status().is4xxClientError());
        mockMvc.perform(delete("/users/{id}", maria.getId()).header("Authorization", bearer(maria)))
                .andExpect(status().is4xxClientError());

        assertThat(userRepository.findByEmail("maria@test.gr")).isPresent();
    }
}
