package gr.barber.booking.dto;

import gr.barber.booking.model.InvitationStatus;
import gr.barber.booking.model.Shop;
import gr.barber.booking.model.ShopInvitation;

import java.time.Instant;

/** Seen by the invited barber (which shop, whose) and by the owner (whom they invited). */
public record ShopInvitationDTO(
        Long id,
        Long shopId,
        String shopName,
        String shopAddress,
        String shopCity,
        String ownerName,
        Long barberId,
        String barberName,
        InvitationStatus status,
        Instant createdAt
) {

    public static ShopInvitationDTO from(ShopInvitation invitation) {
        Shop shop = invitation.getShop();
        return new ShopInvitationDTO(
                invitation.getId(),
                shop.getId(),
                shop.getName(),
                shop.getAddress(),
                shop.getCity(),
                shop.getOwner().getUser().getName(),
                invitation.getBarber().getId(),
                invitation.getBarber().getUser().getName(),
                invitation.getStatus(),
                invitation.getCreatedAt());
    }
}
