package gr.barber.booking.exception;

/** A password that is long enough but too easy to guess. */
public class WeakPasswordException extends RuntimeException {

    public WeakPasswordException(String message) {
        super(message);
    }

}
