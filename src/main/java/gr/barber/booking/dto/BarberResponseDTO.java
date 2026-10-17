package gr.barber.booking.dto;

import gr.barber.booking.media.MediaController;
import gr.barber.booking.model.Barber;
import gr.barber.booking.model.BarberStatus;
import gr.barber.booking.model.Shop;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record BarberResponseDTO(
        Long id,
        String slug,
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
        int bookingWindowDays,
        Long shopId,
        String shopName,
        // Filled in for the public listing and profile; null/0 elsewhere.
        String coverUrl,
        long photoCount,
        BigDecimal fromPrice,
        OffsetDateTime nextAvailable
) {

    /** What customers see on a barber's card besides the profile itself. */
    public record Listing(String coverKey, long photoCount, BigDecimal fromPrice, OffsetDateTime nextAvailable) {
        public static final Listing NONE = new Listing(null, 0, null, null);
    }

    public static BarberResponseDTO from(Barber barber) {
        return from(barber, Listing.NONE);
    }

    public static BarberResponseDTO from(Barber barber, Listing listing) {
        Shop shop = barber.getShop();
        return new BarberResponseDTO(
                barber.getId(),
                barber.getSlug(),
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
                barber.getBookingWindowDays(),
                shop != null ? shop.getId() : null,
                shop != null ? shop.getName() : null,
                MediaController.url(listing.coverKey()),
                listing.photoCount(),
                listing.fromPrice(),
                listing.nextAvailable()
        );
    }
}
