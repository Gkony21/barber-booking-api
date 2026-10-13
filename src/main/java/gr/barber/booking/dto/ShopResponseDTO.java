package gr.barber.booking.dto;

import gr.barber.booking.model.Shop;

public record ShopResponseDTO(
        Long id,
        String name,
        String address,
        String city,
        String phoneNumber,
        Double latitude,
        Double longitude,
        Long ownerBarberId
) {

    public static ShopResponseDTO from(Shop shop) {
        return new ShopResponseDTO(
                shop.getId(),
                shop.getName(),
                shop.getAddress(),
                shop.getCity(),
                shop.getPhoneNumber(),
                shop.getLatitude(),
                shop.getLongitude(),
                shop.getOwner().getId()
        );
    }
}
