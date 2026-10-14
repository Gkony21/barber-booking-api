package gr.barber.booking.dto;

import gr.barber.booking.media.MediaController;
import gr.barber.booking.model.Barber;
import gr.barber.booking.model.BarberStatus;
import gr.barber.booking.model.User;

import java.time.Instant;

/** What an admin needs to decide on a barber: who they are and how to reach them. */
public record AdminBarberResponseDTO(
        Long id,
        Long userId,
        String name,
        String avatarUrl,
        String email,
        String phoneNumber,
        String specialty,
        String address,
        String city,
        String shopName,
        BarberStatus status,
        Instant createdAt
) {

    public static AdminBarberResponseDTO from(Barber barber) {
        User user = barber.getUser();
        return new AdminBarberResponseDTO(
                barber.getId(),
                user.getId(),
                user.getName(),
                MediaController.url(barber.getAvatarKey()),
                user.getEmail(),
                user.getPhoneNumber(),
                barber.getSpecialty(),
                barber.getAddress(),
                barber.getCity(),
                barber.getShop() != null ? barber.getShop().getName() : null,
                barber.getStatus(),
                barber.getCreatedAt()
        );
    }
}
