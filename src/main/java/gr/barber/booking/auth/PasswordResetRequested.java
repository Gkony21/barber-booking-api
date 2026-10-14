package gr.barber.booking.auth;

// Published inside the request's transaction; the email is sent only after it commits.
public record PasswordResetRequested(String email, String token) {
}
