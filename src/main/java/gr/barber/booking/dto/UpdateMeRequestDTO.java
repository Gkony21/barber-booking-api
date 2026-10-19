package gr.barber.booking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** What a person can change about themselves: their name and mobile number. */
public record UpdateMeRequestDTO(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 30) String phoneNumber
) {
}
