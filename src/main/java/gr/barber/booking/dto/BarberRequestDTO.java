package gr.barber.booking.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record BarberRequestDTO(
        @Size(max = 1000) String bio,
        @PositiveOrZero @Max(80) Integer experienceYears,
        @Size(max = 100) String specialty,
        @Size(max = 255) String address,
        @Size(max = 100) String city,
        @DecimalMin("-90") @DecimalMax("90") Double latitude,
        @DecimalMin("-180") @DecimalMax("180") Double longitude,
        // Null keeps the current setting (on by default).
        Boolean autoConfirmBookings
) {
}
