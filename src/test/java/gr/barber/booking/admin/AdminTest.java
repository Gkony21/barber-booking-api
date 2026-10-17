package gr.barber.booking.admin;

import gr.barber.booking.config.AdminBootstrap;
import gr.barber.booking.model.Barber;
import gr.barber.booking.model.BarberStatus;
import gr.barber.booking.model.Role;
import gr.barber.booking.model.User;
import gr.barber.booking.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.http.MediaType;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminTest extends IntegrationTest {

    @Test
    void configuredEmailsBecomeAdminsOnStartup() {
        createUser("Owner", "owner@test.gr", Role.CUSTOMER);
        createUser("Other", "other@test.gr", Role.CUSTOMER);

        new AdminBootstrap(userRepository, List.of(" Owner@Test.gr ", "missing@test.gr", ""))
                .run(new DefaultApplicationArguments());

        assertThat(userRepository.findByEmail("owner@test.gr").orElseThrow().getRole()).isEqualTo(Role.ADMIN);
        assertThat(userRepository.findByEmail("other@test.gr").orElseThrow().getRole()).isEqualTo(Role.CUSTOMER);
    }

    // Someone could register the admin's address first with their own password; until the
    // address is verified, nothing proves the account belongs to the admin.
    @Test
    void unverifiedAccountsAreNotMadeAdmin() {
        User squatter = createUser("Someone", "owner@test.gr", Role.CUSTOMER);
        squatter.setEmailVerified(false);
        userRepository.save(squatter);

        new AdminBootstrap(userRepository, List.of("owner@test.gr")).run(new DefaultApplicationArguments());

        User account = userRepository.findByEmail("owner@test.gr").orElseThrow();
        assertThat(account.getRole()).isEqualTo(Role.CUSTOMER);
        assertThat(account.isEmailVerified()).isFalse();
    }

    @Test
    void adminSeesPendingBarbersWithContactDetailsAndApprovesThem() throws Exception {
        User admin = createUser("Admin", "admin@test.gr", Role.ADMIN);
        User barberUser = createUser("Nikos", "nikos@test.gr", Role.BARBER);
        Barber barber = barberRepository.save(new Barber(barberUser));

        mockMvc.perform(get("/admin/barbers").header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Nikos"))
                .andExpect(jsonPath("$[0].email").value("nikos@test.gr"))
                .andExpect(jsonPath("$[0].phoneNumber").value("6900000000"))
                .andExpect(jsonPath("$[0].status").value("PENDING"));

        mockMvc.perform(patch("/admin/barbers/{id}/status", barber.getId())
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"APPROVED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        assertThat(barberRepository.findById(barber.getId()).orElseThrow().getStatus()).isEqualTo(BarberStatus.APPROVED);
        mockMvc.perform(get("/barbers")).andExpect(jsonPath("$[0].name").value("Nikos"));
    }

    @Test
    void onlyAdminsCanManageBarbers() throws Exception {
        User customer = createUser("Maria", "maria@test.gr", Role.CUSTOMER);
        User barberUser = createUser("Nikos", "nikos@test.gr", Role.BARBER);

        mockMvc.perform(get("/admin/barbers").header("Authorization", bearer(customer))).andExpect(status().isForbidden());
        mockMvc.perform(get("/admin/barbers").header("Authorization", bearer(barberUser))).andExpect(status().isForbidden());
        mockMvc.perform(get("/admin/barbers")).andExpect(status().isUnauthorized());
    }
}
