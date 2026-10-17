package gr.barber.booking.shop;

import com.jayway.jsonpath.JsonPath;
import gr.barber.booking.model.Barber;
import gr.barber.booking.model.Role;
import gr.barber.booking.model.User;
import gr.barber.booking.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Barbers join a shop only by accepting the owner's invitation. */
class ShopInvitationTest extends IntegrationTest {

    private User ownerUser;
    private User nikosUser;
    private Barber nikos;
    private long shopId;

    @BeforeEach
    void setUp() throws Exception {
        ownerUser = createUser("Owner", "owner@test.gr", Role.BARBER);
        createApprovedBarber(ownerUser, true);
        nikosUser = createUser("Nikos", "nikos@test.gr", Role.BARBER);
        nikos = createApprovedBarber(nikosUser, true);
        shopId = idOf(createShop(ownerUser, "Fade House").andExpect(status().isCreated()));
    }

    @Test
    void invitedBarberJoinsOnlyAfterAccepting() throws Exception {
        invite(ownerUser, "Nikos@Test.gr")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.barberName").value("Nikos"))
                .andExpect(jsonPath("$.status").value("PENDING"));

        // The invited barber hears about it by email, with the shop as a labelled value.
        ArgumentCaptor<SimpleMailMessage> mail = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(mail.capture());
        assertThat(mail.getValue().getTo()).containsExactly("nikos@test.gr");
        assertThat(mail.getValue().getText()).contains("Κατάστημα: Fade House", "/barber/profile");

        // Not a member yet.
        mockMvc.perform(get("/barbers").param("shopId", String.valueOf(shopId))).andExpect(jsonPath("$", hasSize(1)));

        long invitationId = idOf(mockMvc.perform(get("/barbers/me/invitations").header("Authorization", bearer(nikosUser)))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].shopName").value("Fade House"))
                .andExpect(jsonPath("$[0].ownerName").value("Owner")), "$[0].id");

        mockMvc.perform(post("/barbers/me/invitations/{id}/accept", invitationId).header("Authorization", bearer(nikosUser)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/barbers/{id}", nikos.getId()))
                .andExpect(jsonPath("$.shopName").value("Fade House"))
                .andExpect(jsonPath("$.shopAddress").value("Ermou 1"))
                .andExpect(jsonPath("$.shopCity").value("Athens"));
        mockMvc.perform(get("/barbers/me/invitations").header("Authorization", bearer(nikosUser))).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void ownersCannotAddBarbersWithoutAsking() throws Exception {
        mockMvc.perform(post("/shops/{id}/barbers", shopId)
                        .header("Authorization", bearer(ownerUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nikos@test.gr\"}"))
                .andExpect(status().is4xxClientError());

        assertThat(barberRepository.findById(nikos.getId()).orElseThrow().getShop()).isNull();
    }

    @Test
    void invitationsAreCheckedAndCodedForTheApp() throws Exception {
        invite(ownerUser, "nobody@test.gr").andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NO_BARBER_FOR_EMAIL"));
        invite(ownerUser, "owner@test.gr").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CANNOT_INVITE_SELF"));

        User pendingUser = createUser("New", "new@test.gr", Role.BARBER);
        barberRepository.save(new Barber(pendingUser));
        invite(ownerUser, "new@test.gr").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BARBER_NOT_APPROVED"));

        invite(ownerUser, "nikos@test.gr").andExpect(status().isCreated());
        invite(ownerUser, "nikos@test.gr").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ALREADY_INVITED"));

        // Only the owner invites.
        invite(nikosUser, "new@test.gr").andExpect(status().isForbidden());
    }

    @Test
    void declinedOrWithdrawnInvitationsCannotBeAccepted() throws Exception {
        long declined = idOf(invite(ownerUser, "nikos@test.gr"));
        mockMvc.perform(post("/barbers/me/invitations/{id}/decline", declined).header("Authorization", bearer(nikosUser)))
                .andExpect(status().isNoContent());
        accept(nikosUser, declined).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVITATION_CLOSED"));

        long withdrawn = idOf(invite(ownerUser, "nikos@test.gr"));
        mockMvc.perform(get("/shops/{id}/invitations", shopId).header("Authorization", bearer(ownerUser)))
                .andExpect(jsonPath("$", hasSize(1)));
        mockMvc.perform(delete("/shops/{id}/invitations/{inv}", shopId, withdrawn).header("Authorization", bearer(ownerUser)))
                .andExpect(status().isNoContent());
        accept(nikosUser, withdrawn).andExpect(status().isBadRequest());

        assertThat(barberRepository.findById(nikos.getId()).orElseThrow().getShop()).isNull();
    }

    // A barber works in one shop: joining one drops the other open invitations.
    @Test
    void acceptingOneInvitationWithdrawsTheOthers() throws Exception {
        User otherOwner = createUser("Other", "other@test.gr", Role.BARBER);
        createApprovedBarber(otherOwner, true);
        long otherShop = idOf(createShop(otherOwner, "Blade Bar"));

        long first = idOf(invite(ownerUser, "nikos@test.gr"));
        long second = idOf(mockMvc.perform(post("/shops/{id}/invitations", otherShop)
                .header("Authorization", bearer(otherOwner))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"nikos@test.gr\"}")));

        accept(nikosUser, first).andExpect(status().isOk());
        accept(nikosUser, second).andExpect(status().isBadRequest());
        mockMvc.perform(get("/shops/{id}/invitations", otherShop).header("Authorization", bearer(otherOwner)))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void membersCanLeaveButTheOwnerCannot() throws Exception {
        accept(nikosUser, idOf(invite(ownerUser, "nikos@test.gr")));

        mockMvc.perform(delete("/shops/{id}/barbers/{b}", shopId, nikos.getId()).header("Authorization", bearer(nikosUser)))
                .andExpect(status().isNoContent());
        assertThat(barberRepository.findById(nikos.getId()).orElseThrow().getShop()).isNull();

        long ownerId = barberRepository.findByUserEmail("owner@test.gr").orElseThrow().getId();
        mockMvc.perform(delete("/shops/{id}/barbers/{b}", shopId, ownerId).header("Authorization", bearer(ownerUser)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("OWNER_CANNOT_LEAVE"));
    }

    private ResultActions createShop(User owner, String name) throws Exception {
        return mockMvc.perform(post("/shops")
                .header("Authorization", bearer(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"%s","address":"Ermou 1","city":"Athens"}""".formatted(name)));
    }

    private ResultActions invite(User owner, String email) throws Exception {
        return mockMvc.perform(post("/shops/{id}/invitations", shopId)
                .header("Authorization", bearer(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"%s"}""".formatted(email)));
    }

    private ResultActions accept(User barber, long invitationId) throws Exception {
        return mockMvc.perform(post("/barbers/me/invitations/{id}/accept", invitationId).header("Authorization", bearer(barber)));
    }

    private static long idOf(ResultActions result) throws Exception {
        return idOf(result, "$.id");
    }

    private static long idOf(ResultActions result, String path) throws Exception {
        return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(), path)).longValue();
    }
}
