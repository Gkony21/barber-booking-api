package gr.barber.booking.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @param startTime local time at the barber, e.g. "2026-10-20T10:30"
 */
public record AppointmentRequestDTO(
        @NotNull Long barberId,
        @NotNull Long serviceId,
        @NotNull LocalDateTime startTime,
        @Size(max = 500) String note,
        // Extras on top of the main service, in the order chosen; null or empty for none.
        @Size(max = 10) List<Long> extraIds
) {
}
