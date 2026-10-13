package gr.barber.booking.model;

public enum AppointmentStatus {
    PENDING,
    CONFIRMED,
    REJECTED,
    CANCELLED;

    // PENDING and CONFIRMED appointments occupy the barber's time.
    public boolean isActive() {
        return this == PENDING || this == CONFIRMED;
    }
}
