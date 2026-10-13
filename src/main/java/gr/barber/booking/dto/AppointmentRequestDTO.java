package gr.barber.booking.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * @param startTime local time at the barber, e.g. "2026-10-20T10:30"
 */
public record AppointmentRequestDTO(
        @NotNull Long barberId,
        @NotNull Long serviceId,
        @NotNull LocalDateTime startTime,
        @Size(max = 500) String note
) {
}
