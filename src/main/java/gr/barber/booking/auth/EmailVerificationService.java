package gr.barber.booking.auth;

import gr.barber.booking.exception.BadRequestException;
import gr.barber.booking.model.EmailVerificationToken;
import gr.barber.booking.model.User;
import gr.barber.booking.repository.EmailVerificationTokenRepository;
import gr.barber.booking.repository.UserRepository;
import gr.barber.booking.service.UserService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class EmailVerificationService {

    private static final Duration TOKEN_LIFETIME = Duration.ofHours(24);
    private static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);

    private final SecureRandom secureRandom = new SecureRandom();

    private final EmailVerificationTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    public EmailVerificationService(EmailVerificationTokenRepository tokenRepository,
                                    UserRepository userRepository,
                                    ApplicationEventPublisher eventPublisher) {
        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
        this.eventPublisher = eventPublisher;
    }

    // Replaces any earlier unused link, so only the most recent email works.
    @Transactional
    public void sendVerification(User user) {

        tokenRepository.deleteUnusedByUserId(user.getId());

        String token = generateToken();
        tokenRepository.save(new EmailVerificationToken(user, hash(token), Instant.now().plus(TOKEN_LIFETIME)));

        eventPublisher.publishEvent(new VerificationEmailRequested(user.getEmail(), user.getName(), token));
    }

    @Transactional
    public void verify(String token) {

        Instant now = Instant.now();

        EmailVerificationToken verificationToken = tokenRepository.findByTokenHash(hash(token))
                .filter(t -> t.isUsable(now))
                .orElseThrow(() -> new BadRequestException("Invalid or expired verification link"));

        verificationToken.markUsed(now);
        verificationToken.getUser().setEmailVerified(true);
    }

    // Always succeeds silently so the endpoint cannot be used to check which emails are registered.
    @Transactional
    public void resend(String email) {

        userRepository.findByEmail(UserService.normalizeEmail(email))
                .filter(user -> !user.isEmailVerified())
                .filter(this::cooldownPassed)
                .ifPresent(this::sendVerification);
    }

    private boolean cooldownPassed(User user) {
        return tokenRepository.findTopByUserIdOrderByCreatedAtDesc(user.getId())
                .map(last -> last.getCreatedAt().plus(RESEND_COOLDOWN).isBefore(Instant.now()))
                .orElse(true);
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
