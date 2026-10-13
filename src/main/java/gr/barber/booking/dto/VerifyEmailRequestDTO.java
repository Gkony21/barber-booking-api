package gr.barber.booking.dto;

import jakarta.validation.constraints.NotBlank;

public record VerifyEmailRequestDTO(@NotBlank String token) {
}
