package gr.barber.booking.dto;

import jakarta.validation.constraints.Size;

/** A blank or null caption removes it. */
public record PhotoCaptionDTO(@Size(max = 80) String caption) {
}
