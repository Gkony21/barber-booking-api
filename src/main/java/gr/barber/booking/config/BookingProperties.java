package gr.barber.booking.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.time.ZoneId;

/**
 * Booking rules, overridable with app.booking.* properties.
 *
 * @param timeZone            zone of the barbers' working hours; all bookings are interpreted in it
 * @param cancellationCutoff  how long before a confirmed appointment the customer can still cancel
 * @param maxActivePerCustomer how many upcoming appointments (pending or confirmed, any barber) a
 *                            customer may hold at once, so nobody can block a barber's whole week
 */
@ConfigurationProperties("app.booking")
public record BookingProperties(
        @DefaultValue("Europe/Athens") ZoneId timeZone,
        @DefaultValue("2h") Duration cancellationCutoff,
        @DefaultValue("2") int maxActivePerCustomer
) {
}
