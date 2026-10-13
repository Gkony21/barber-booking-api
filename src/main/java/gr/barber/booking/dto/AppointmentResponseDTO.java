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
        CancelledBy cancelledBy,
        String note
) {

    public static AppointmentResponseDTO from(Appointment appointment, ZoneId zone) {
        return new AppointmentResponseDTO(
                appointment.getId(),
                appointment.getBarber().getId(),
                appointment.getBarber().getUser().getName(),
                appointment.getCustomer().getId(),
                appointment.getCustomer().getName(),
                appointment.getCustomer().getPhoneNumber(),
                appointment.getService().getId(),
                appointment.getServiceName(),
                Duration.between(appointment.getStartAt(), appointment.getEndAt()).toMinutes(),
                appointment.getPrice(),
                appointment.getStartAt().atZone(zone).toOffsetDateTime(),
                appointment.getEndAt().atZone(zone).toOffsetDateTime(),
                appointment.getStatus(),
                appointment.getCancelledBy(),
                appointment.getCustomerNote()
        );
    }
}
