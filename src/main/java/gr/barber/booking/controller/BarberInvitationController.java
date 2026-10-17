package gr.barber.booking.controller;

import gr.barber.booking.dto.ShopInvitationDTO;
import gr.barber.booking.dto.ShopResponseDTO;
import gr.barber.booking.service.ShopService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Shop invitations the logged-in barber has received. */
@RestController
@RequestMapping("/barbers/me/invitations")
public class BarberInvitationController {

    private final ShopService shopService;

    public BarberInvitationController(ShopService shopService) {
        this.shopService = shopService;
    }

    @GetMapping
    public List<ShopInvitationDTO> getMyInvitations(Authentication authentication) {
        return shopService.getMyInvitations(authentication.getName());
    }

    @PostMapping("/{id}/accept")
    public ShopResponseDTO accept(@PathVariable Long id, Authentication authentication) {
        return shopService.acceptInvitation(id, authentication.getName());
    }

    @PostMapping("/{id}/decline")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void decline(@PathVariable Long id, Authentication authentication) {
        shopService.declineInvitation(id, authentication.getName());
    }
}
