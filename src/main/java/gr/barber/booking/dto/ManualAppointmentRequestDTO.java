package gr.barber.booking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * A booking the barber enters for a phone or walk-in customer.
 *
 * @param startTime local time at the barber, e.g. "2026-10-20T10:30"
 */
public record ManualAppointmentRequestDTO(
        @NotNull Long serviceId,
        @NotNull LocalDateTime startTime,
        @NotBlank @Size(max = 100) String customerName,
        @Size(max = 30) String customerPhone,
        @Size(max = 500) String note
) {
}
