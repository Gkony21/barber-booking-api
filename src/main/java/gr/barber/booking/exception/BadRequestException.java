package gr.barber.booking.exception;

public class BadRequestException extends ApiException {

    public BadRequestException(String message) {
        super(null, message);
    }

    public BadRequestException(String code, String message) {
        super(code, message);
    }

}
