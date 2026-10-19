package gr.barber.booking.notification;

import gr.barber.booking.model.Appointment;
import gr.barber.booking.model.AppointmentStatus;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * Published when an appointment changes. Carries plain values (not the entity) because
 * listeners run after the transaction has committed.
 */
public record AppointmentEvent(
        AppointmentEventType type,
        String customerEmail,
        String customerName,
        String customerPhone,
        String barberEmail,
        String barberName,
        String serviceName,
        List<String> extraNames,
        BigDecimal price,
        ZonedDateTime startTime,
        // Waiting for the barber's answer.
        boolean pending
) {

    public static AppointmentEvent of(AppointmentEventType type, Appointment appointment, ZoneId zone) {
        return new AppointmentEvent(
                type,
                appointment.getCustomer() != null ? appointment.getCustomer().getEmail() : null,
                appointment.getCustomerName(),
                appointment.getCustomerPhone(),
                appointment.getBarber().getUser().getEmail(),
                appointment.getBarber().getUser().getName(),
                appointment.getServiceName(),
                appointment.getExtras().stream().map(extra -> extra.getName()).toList(),
                appointment.getPrice(),
                appointment.getStartAt().atZone(zone),
                appointment.getStatus() == AppointmentStatus.PENDING
        );
    }
}
