package gr.barber.booking.notification;

public enum AppointmentEventType {
    BOOKED_CONFIRMED,
    BOOKED_PENDING,
    CONFIRMED_BY_BARBER,
    REJECTED_BY_BARBER,
    CANCELLED_BY_CUSTOMER,
    CANCELLED_BY_BARBER
}
