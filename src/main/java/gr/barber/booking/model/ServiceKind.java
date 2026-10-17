package gr.barber.booking.model;

public enum ServiceKind {
    /** A service or package; each appointment has exactly one. */
    MAIN,
    /** Added on top of a main service, e.g. a wash or a design; any number per appointment. */
    EXTRA
}
