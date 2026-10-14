package gr.barber.booking.dto;

import jakarta.validation.constraints.NotBlank;

/** The current password, so a borrowed phone with an open session cannot delete the account. */
public record DeleteAccountRequestDTO(@NotBlank String password) {
}
