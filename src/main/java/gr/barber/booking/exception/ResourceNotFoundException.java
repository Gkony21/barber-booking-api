package gr.barber.booking.exception;

public class ResourceNotFoundException extends ApiException {

    public ResourceNotFoundException(String message) {
        super(null, message);
    }

    public ResourceNotFoundException(String code, String message) {
        super(code, message);
    }

}
