package gr.barber.booking.dto;

import gr.barber.booking.model.Appointment;
import gr.barber.booking.model.AppointmentStatus;
import gr.barber.booking.model.CancelledBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;

public record AppointmentResponseDTO(
        Long id,
        Long barberId,
        String barberName,
        Long customerId,
        String customerName,
        String customerPhone,
        Long serviceId,
        String serviceName,
        long durationMinutes,
        BigDecimal price,
        OffsetDateTime startTime,
        OffsetDateTime endTime,
        AppointmentStatus status,
        // Entered by the barber for a phone or walk-in customer without an account.
        boolean manual,
        CancelledBy cancelledBy,
        String note
) {

    public static AppointmentResponseDTO from(Appointment appointment, ZoneId zone) {
        return new AppointmentResponseDTO(
                appointment.getId(),
                appointment.getBarber().getId(),
                appointment.getBarber().getUser().getName(),
                appointment.getCustomer() != null ? appointment.getCustomer().getId() : null,
                appointment.getCustomerName(),
                appointment.getCustomerPhone(),
                appointment.getService().getId(),
                appointment.getServiceName(),
                Duration.between(appointment.getStartAt(), appointment.getEndAt()).toMinutes(),
                appointment.getPrice(),
                appointment.getStartAt().atZone(zone).toOffsetDateTime(),
                appointment.getEndAt().atZone(zone).toOffsetDateTime(),
                appointment.getStatus(),
                appointment.isManual(),
                appointment.getCancelledBy(),
                appointment.getCustomerNote()
        );
    }
}
