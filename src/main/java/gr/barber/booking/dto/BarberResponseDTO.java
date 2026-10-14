package gr.barber.booking.dto;

import gr.barber.booking.media.MediaController;
import gr.barber.booking.model.Barber;
import gr.barber.booking.model.BarberStatus;
import gr.barber.booking.model.Shop;

public record BarberResponseDTO(
        Long id,
        String name,
        String avatarUrl,
        String bio,
        Integer experienceYears,
        String specialty,
        String address,
        String city,
        Double latitude,
        Double longitude,
        BarberStatus status,
        boolean autoConfirmBookings,
        int slotStepMinutes,
        Long shopId,
        String shopName
) {

    public static BarberResponseDTO from(Barber barber) {
        Shop shop = barber.getShop();
        return new BarberResponseDTO(
                barber.getId(),
                barber.getUser().getName(),
                MediaController.url(barber.getAvatarKey()),
                barber.getBio(),
                barber.getExperienceYears(),
                barber.getSpecialty(),
                barber.getAddress(),
                barber.getCity(),
                barber.getLatitude(),
                barber.getLongitude(),
                barber.getStatus(),
                barber.isAutoConfirmBookings(),
                barber.getSlotStepMinutes(),
                shop != null ? shop.getId() : null,
                shop != null ? shop.getName() : null
        );
    }
}
