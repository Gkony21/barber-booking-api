package gr.barber.booking.dto;

import gr.barber.booking.model.DayStatus;

import java.time.LocalDate;

public record DayStatusDTO(LocalDate date, DayStatus status) {
}
