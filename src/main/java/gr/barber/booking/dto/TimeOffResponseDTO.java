package gr.barber.booking.dto;

import java.time.LocalDate;

/**
 * @param activeAppointments appointments still booked inside this period; the barber
 *                           should cancel them, since time off only blocks new bookings
 */
public record TimeOffResponseDTO(
        Long id,
        LocalDate startDate,
        LocalDate endDate,
        String reason,
        int activeAppointments
) {
}
