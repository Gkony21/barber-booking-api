package gr.barber.booking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The current password proves it is the account owner, not someone with a borrowed phone. */
public record ChangePasswordRequestDTO(
        @NotBlank String currentPassword,
        @NotBlank @Size(min = 8, max = 100) String newPassword
) {
}
