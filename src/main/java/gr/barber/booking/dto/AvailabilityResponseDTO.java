package gr.barber.booking.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * @param startTimes free start times on that date, formatted "HH:mm"
 */
public record AvailabilityResponseDTO(
        Long barberId,
        Long serviceId,
        LocalDate date,
        int durationMinutes,
        List<String> startTimes
) {
}
