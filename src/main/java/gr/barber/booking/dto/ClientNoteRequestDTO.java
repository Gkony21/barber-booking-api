package gr.barber.booking.dto;

import jakarta.validation.constraints.Size;

/** The barber's note about a customer; empty removes it. */
public record ClientNoteRequestDTO(@Size(max = 1000) String note) {
}
