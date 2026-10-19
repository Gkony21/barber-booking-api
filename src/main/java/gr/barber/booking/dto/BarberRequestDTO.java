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
        Boolean autoConfirmBookings,
        // 15 or 30; null keeps the current setting (30 by default).
        Integer slotStepMinutes,
        // Public link, e.g. "giorgos"; null keeps the current one.
        @Size(max = 30) String slug,
        // 14, 30 or 60 days; null keeps the current setting (14 by default).
        Integer bookingWindowDays,
        // 30, 60 or 120 minutes; null keeps the current setting (60 by default).
        Integer minNoticeMinutes
) {
}
