package gr.barber.booking.notification;

public enum AppointmentEventType {
    BOOKED_CONFIRMED,
    BOOKED_PENDING,
    CONFIRMED_BY_BARBER,
    REJECTED_BY_BARBER,
    CANCELLED_BY_CUSTOMER,
    CANCELLED_BY_BARBER,
    RESCHEDULED_BY_CUSTOMER,
    // A confirmed appointment the customer asked to move; the events carry the change.
    CHANGE_REQUESTED,
    CHANGE_ACCEPTED,
    CHANGE_DECLINED,
    CHANGE_WITHDRAWN,
    // The barber moved a confirmed appointment or changed its services.
    CHANGED_BY_BARBER,
    REQUEST_WAITING,
    REQUEST_EXPIRED,
    REMINDER_DAY,
    REMINDER_SOON
}
