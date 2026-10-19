package gr.barber.booking.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * @param activeAppointments appointments still booked inside this period; the barber
 *                           should cancel them, since time off only blocks new bookings
 */
public record TimeOffResponseDTO(
        Long id,
        LocalDate startDate,
        LocalDate endDate,
        String reason,
        // Null for whole days off.
        @JsonFormat(pattern = "HH:mm") LocalTime startTime,
        @JsonFormat(pattern = "HH:mm") LocalTime endTime,
        int activeAppointments
) {
}
