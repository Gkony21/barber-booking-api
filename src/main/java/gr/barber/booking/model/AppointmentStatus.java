package gr.barber.booking.model;

public enum AppointmentStatus {
    PENDING,
    CONFIRMED,
    REJECTED,
    CANCELLED,
    // A request the barber did not answer in time; closed automatically before it would start.
    EXPIRED,
    // Confirmed, but the customer did not turn up; marked by the barber.
    NO_SHOW;

    // PENDING and CONFIRMED appointments occupy the barber's time.
    public boolean isActive() {
        return this == PENDING || this == CONFIRMED;
    }
}
