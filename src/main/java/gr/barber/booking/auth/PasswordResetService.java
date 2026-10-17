package gr.barber.booking.auth;

import gr.barber.booking.exception.BadRequestException;
import gr.barber.booking.model.PasswordResetToken;
import gr.barber.booking.model.User;
import gr.barber.booking.repository.PasswordResetTokenRepository;
import gr.barber.booking.repository.UserRepository;
import gr.barber.booking.service.UserService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Service
public class PasswordResetService {

    // Short-lived: a reset link in an old email should not keep working.
    private static final Duration TOKEN_LIFETIME = Duration.ofHours(1);
    private static final Duration REQUEST_COOLDOWN = Duration.ofSeconds(60);

    private final PasswordResetTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public PasswordResetService(PasswordResetTokenRepository tokenRepository,
                                UserRepository userRepository,
                                PasswordEncoder passwordEncoder,
                                PasswordPolicy passwordPolicy,
                                ApplicationEventPublisher eventPublisher,
                                Clock clock) {
        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    // Always succeeds silently so the endpoint cannot be used to check which emails are registered.
    @Transactional
    public void requestReset(String email) {
        userRepository.findByEmail(UserService.normalizeEmail(email))
                .filter(this::cooldownPassed)
                .ifPresent(user -> {
                    tokenRepository.deleteUnusedByUserId(user.getId());
                    String token = SecureTokens.generate();
                    tokenRepository.save(new PasswordResetToken(
                            user, SecureTokens.hash(token), clock.instant().plus(TOKEN_LIFETIME)));
                    eventPublisher.publishEvent(new PasswordResetRequested(user.getEmail(), token));
                });
    }

    @Transactional
    public void resetPassword(String token, String newPassword) {
        Instant now = clock.instant();
        PasswordResetToken resetToken = tokenRepository.findByTokenHash(SecureTokens.hash(token))
                .filter(t -> t.isUsable(now))
                .orElseThrow(() -> new BadRequestException("INVALID_RESET_LINK", "Invalid or expired reset link"));

        User user = resetToken.getUser();
        passwordPolicy.check(newPassword, user.getEmail());

        user.setPassword(passwordEncoder.encode(newPassword));
        // Anyone still signed in with the old password (a lost phone, a shared computer) is signed out.
        user.invalidateTokens();
        // Opening the link proves the person controls this address.
        user.setEmailVerified(true);
        resetToken.markUsed(now);
    }

    private boolean cooldownPassed(User user) {
        return tokenRepository.findTopByUserIdOrderByCreatedAtDesc(user.getId())
                .map(last -> last.getCreatedAt().plus(REQUEST_COOLDOWN).isBefore(Instant.now()))
                .orElse(true);
    }
}
