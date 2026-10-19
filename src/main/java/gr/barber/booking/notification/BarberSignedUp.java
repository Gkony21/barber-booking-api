package gr.barber.booking.notification;

/** A barber created their profile; admins are told so they can review it. */
public record BarberSignedUp(String name, String email, String phoneNumber, String city) {
}
