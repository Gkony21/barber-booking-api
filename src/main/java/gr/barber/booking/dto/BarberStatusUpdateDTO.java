package gr.barber.booking.dto;

import gr.barber.booking.model.BarberStatus;
import jakarta.validation.constraints.NotNull;

public record BarberStatusUpdateDTO(@NotNull BarberStatus status) {
}
