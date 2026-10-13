package gr.barber.booking.auth;

// Published inside the registration transaction; the email is sent only after it commits.
public record VerificationEmailRequested(String email, String name, String token) {
}
