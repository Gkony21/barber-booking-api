package gr.barber.booking.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

/**
 * The barber's change to a confirmed appointment: a new time and/or services. Name, phone and note
 * only apply to appointments the barber entered; an app customer's own details stay theirs.
 *
 * @param startTime local time at the barber, e.g. "2026-10-20T10:30"
 */
public record BarberAppointmentUpdateDTO(
        @NotNull Long serviceId,
        @NotNull LocalDateTime startTime,
        @Size(max = 10) List<Long> extraIds,
        @Size(max = 100) String customerName,
        @Size(max = 30) String customerPhone,
        @Size(max = 500) String note
) {
}
