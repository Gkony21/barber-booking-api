package gr.barber.booking.notification;

import gr.barber.booking.model.Appointment;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.ZonedDateTime;

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
        BigDecimal price,
        ZonedDateTime startTime
) {

    public static AppointmentEvent of(AppointmentEventType type, Appointment appointment, ZoneId zone) {
        return new AppointmentEvent(
                type,
                appointment.getCustomer().getEmail(),
                appointment.getCustomer().getName(),
                appointment.getCustomer().getPhoneNumber(),
                appointment.getBarber().getUser().getEmail(),
                appointment.getBarber().getUser().getName(),
                appointment.getServiceName(),
                appointment.getPrice(),
                appointment.getStartAt().atZone(zone)
        );
    }
}
