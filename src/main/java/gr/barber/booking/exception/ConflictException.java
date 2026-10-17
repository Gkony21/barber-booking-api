package gr.barber.booking.exception;

public class ConflictException extends ApiException {

    public ConflictException(String message) {
        super(null, message);
    }

    public ConflictException(String code, String message) {
        super(code, message);
    }

}
