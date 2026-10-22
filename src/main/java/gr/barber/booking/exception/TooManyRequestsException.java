package gr.barber.booking.exception;

public class TooManyRequestsException extends ApiException {

    public TooManyRequestsException(String code, String message) {
        super(code, message);
    }
}
