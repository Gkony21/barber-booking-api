package gr.barber.booking.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/** @param startTime the new local time at the barber, e.g. "2026-10-20T10:30" */
public record RescheduleRequestDTO(@NotNull LocalDateTime startTime) {
}
