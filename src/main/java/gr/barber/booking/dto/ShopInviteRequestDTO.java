package gr.barber.booking.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** The email the invited barber signed up with. */
public record ShopInviteRequestDTO(@NotBlank @Email String email) {
}
