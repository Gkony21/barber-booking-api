package gr.barber.booking.dto;

import java.util.List;

/** A customer with all their appointments at this barber, newest first. */
public record ClientCardDTO(ClientDTO client, List<AppointmentResponseDTO> history) {
}
