package gr.barber.booking.exception;

/**
 * An error the client can explain to the person. The message is for developers (logs, Postman);
 * the code is what the web app turns into Greek text, so the two can change independently.
 */
public abstract class ApiException extends RuntimeException {

    private final String code;

    protected ApiException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
