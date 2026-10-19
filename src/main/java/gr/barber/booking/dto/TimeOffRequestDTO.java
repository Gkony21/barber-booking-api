package gr.barber.booking.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDate;
import java.time.LocalTime;

public record TimeOffRequestDTO(
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        @Size(max = 255) String reason,
        // Both set for a block of hours on a single day (startDate == endDate); both null for whole days.
        @JsonFormat(pattern = "HH:mm") LocalTime startTime,
        @JsonFormat(pattern = "HH:mm") LocalTime endTime
) {
}
