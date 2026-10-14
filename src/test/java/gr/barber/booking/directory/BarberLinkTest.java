package gr.barber.booking.directory;

import gr.barber.booking.config.SlugBackfill;
import gr.barber.booking.model.Barber;
import gr.barber.booking.model.BarberStatus;
import gr.barber.booking.model.Role;
import gr.barber.booking.model.User;
import gr.barber.booking.service.SlugService;
import gr.barber.booking.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BarberLinkTest extends IntegrationTest {

    @Autowired
    private SlugService slugService;

    @Autowired
    private SlugBackfill backfill;

    @Test
    void greekNamesBecomeReadableLatinLinks() {
        assertThat(slugService.generate("Γιώργος", null)).isEqualTo("giorgos");
        assertThat(slugService.generate("Νίκος Παπαδόπουλος", null)).isEqualTo("nikos-papadopoulos");
        assertThat(slugService.generate("Μπάμπης Ουζούνης", null)).isEqualTo("mpampis-ouzounis");
        assertThat(slugService.generate("Barber Nick!", null)).isEqualTo("barber-nick");
        assertThat(slugService.generate("Al", null)).isEqualTo("al-barber");
    }

    @Test
    void newProfilesGetAFreeLinkFromTheName() throws Exception {
        User first = createUser("Γιώργος", "g1@test.gr", Role.BARBER);
        User second = createUser("Γιώργος", "g2@test.gr", Role.BARBER);

        createProfile(first).andExpect(jsonPath("$.slug").value("giorgos"));
        createProfile(second).andExpect(jsonPath("$.slug").value("giorgos-2"));
    }

    @Test
    void barbersCanChooseTheirLinkWithinTheRules() throws Exception {
        User nikos = createUser("Nikos", "nikos@test.gr", Role.BARBER);
        User other = createUser("Other", "other@test.gr", Role.BARBER);
        createProfile(nikos);
        createProfile(other);

        updateLink(nikos, "nick-cuts").andExpect(status().isOk()).andExpect(jsonPath("$.slug").value("nick-cuts"));
        updateLink(other, "nick-cuts").andExpect(status().isConflict());
        updateLink(other, "admin").andExpect(status().isConflict());
        updateLink(other, "no spaces!").andExpect(status().isBadRequest());
        updateLink(other, "ab").andExpect(status().isBadRequest());
    }

    @Test
    void approvedBarbersAreFoundByTheirLink() throws Exception {
        User nikos = createUser("Nikos", "nikos@test.gr", Role.BARBER);
        createProfile(nikos);

        mockMvc.perform(get("/barbers/by-link/nikos")).andExpect(status().isNotFound()); // still pending

        Barber barber = barberRepository.findByUserEmail("nikos@test.gr").orElseThrow();
        barber.setStatus(BarberStatus.APPROVED);
        barberRepository.save(barber);

        mockMvc.perform(get("/barbers/by-link/NIKOS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Nikos"));
    }

    @Test
    void barbersCreatedBeforeLinksGetOneOnStartup() {
        Barber old = createApprovedBarber(createUser("Κώστας", "k@test.gr", Role.BARBER), true);
        assertThat(old.getSlug()).isNull();

        backfill.run(new DefaultApplicationArguments());

        assertThat(barberRepository.findById(old.getId()).orElseThrow().getSlug()).isEqualTo("kostas");
    }

    private ResultActions createProfile(User user) throws Exception {
        return mockMvc.perform(post("/barbers/me").header("Authorization", bearer(user))
                .contentType(MediaType.APPLICATION_JSON).content("{\"city\":\"Αθήνα\"}"));
    }

    private ResultActions updateLink(User user, String slug) throws Exception {
        return mockMvc.perform(put("/barbers/me").header("Authorization", bearer(user))
                .contentType(MediaType.APPLICATION_JSON).content("{\"city\":\"Αθήνα\",\"slug\":\"" + slug + "\"}"));
    }
}
