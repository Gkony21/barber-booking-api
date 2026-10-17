package gr.barber.booking.controller;

import gr.barber.booking.dto.ShopInvitationDTO;
import gr.barber.booking.dto.ShopInviteRequestDTO;
import gr.barber.booking.dto.ShopRequestDTO;
import gr.barber.booking.dto.ShopResponseDTO;
import gr.barber.booking.service.ShopService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/shops")
public class ShopController {

    private final ShopService shopService;

    public ShopController(ShopService shopService) {
        this.shopService = shopService;
    }

    @GetMapping
    public List<ShopResponseDTO> getShops(@RequestParam(required = false) String city) {
        return shopService.getShops(city);
    }

    @GetMapping("/{id}")
    public ShopResponseDTO getShop(@PathVariable Long id) {
        return shopService.getShop(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ShopResponseDTO createShop(@Valid @RequestBody ShopRequestDTO request,
                                      Authentication authentication) {
        return shopService.createShop(authentication.getName(), request);
    }

    @PutMapping("/{id}")
    public ShopResponseDTO updateShop(@PathVariable Long id,
                                      @Valid @RequestBody ShopRequestDTO request,
                                      Authentication authentication) {
        return shopService.updateShop(id, authentication.getName(), request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteShop(@PathVariable Long id, Authentication authentication) {
        shopService.deleteShop(id, authentication.getName());
    }

    // Owners invite barbers by their sign-up email; the barber joins by accepting.
    @PostMapping("/{id}/invitations")
    @ResponseStatus(HttpStatus.CREATED)
    public ShopInvitationDTO invite(@PathVariable Long id,
                                    @Valid @RequestBody ShopInviteRequestDTO request,
                                    Authentication authentication) {
        return shopService.invite(id, authentication.getName(), request.email());
    }

    @GetMapping("/{id}/invitations")
    public List<ShopInvitationDTO> getInvitations(@PathVariable Long id, Authentication authentication) {
        return shopService.getPendingInvitations(id, authentication.getName());
    }

    @DeleteMapping("/{id}/invitations/{invitationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelInvitation(@PathVariable Long id, @PathVariable Long invitationId, Authentication authentication) {
        shopService.cancelInvitation(id, invitationId, authentication.getName());
    }

    @DeleteMapping("/{id}/barbers/{barberId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeBarber(@PathVariable Long id,
                             @PathVariable Long barberId,
                             Authentication authentication) {
        shopService.removeBarber(id, barberId, authentication.getName());
    }

}
