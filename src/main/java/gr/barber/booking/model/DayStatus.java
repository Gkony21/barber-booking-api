package gr.barber.booking.model;

/** How a day looks to a customer choosing when to book. */
public enum DayStatus {
    /** At least one free time for the chosen services. */
    OPEN,
    /** Working, but nothing left that fits. */
    FULL,
    /** Not working: no hours that weekday, or a day off. */
    CLOSED
}
