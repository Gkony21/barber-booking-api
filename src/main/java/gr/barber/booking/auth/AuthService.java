package gr.barber.booking.auth;

import gr.barber.booking.exception.BadRequestException;
import gr.barber.booking.exception.EmailNotVerifiedException;
import gr.barber.booking.exception.TooManyRequestsException;
import gr.barber.booking.exception.UnauthorizedException;
import gr.barber.booking.model.Role;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import gr.barber.booking.dto.UserRequestDTO;
import gr.barber.booking.dto.UserResponseDTO;
import gr.barber.booking.service.PhoneNumbers;
import gr.barber.booking.service.UserService;
import gr.barber.booking.dto.LoginRequestDTO;
import gr.barber.booking.dto.LoginResponseDTO;
import gr.barber.booking.model.User;
import gr.barber.booking.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import gr.barber.booking.security.JwtService;

import java.time.Clock;
import java.time.Duration;

@Service
public class AuthService {

    // Same message for unknown email and wrong password, so login cannot be used to discover accounts.
    private static final String INVALID_CREDENTIALS = "Invalid email or password";

    private final UserService userService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final EmailVerificationService emailVerificationService;
    private final Clock clock;
    private final int dailySignUps;

    public AuthService(UserService userService,
                       UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       EmailVerificationService emailVerificationService,
                       Clock clock,
                       @Value("${app.registration.daily-limit:100}") int dailySignUps) {

        this.userService = userService;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.emailVerificationService = emailVerificationService;
        this.clock = clock;
        this.dailySignUps = dailySignUps;
    }

    @Transactional
    public UserResponseDTO register(UserRequestDTO request) {

        if (request.getRole() != Role.CUSTOMER && request.getRole() != Role.BARBER) {
            throw new BadRequestException("Invalid role. Allowed roles: CUSTOMER, BARBER");
        }

        if (!Boolean.TRUE.equals(request.getAcceptTerms())) {
            throw new BadRequestException("TERMS_NOT_ACCEPTED", "You need to accept the terms and privacy policy");
        }

        // Each sign-up sends an email, and the free mail plan has a daily limit that booking emails need too;
        // the per-address rate limit cannot stop someone who uses many addresses.
        if (userRepository.countByCreatedAtAfter(clock.instant().minus(Duration.ofDays(1))) >= dailySignUps) {
            throw new TooManyRequestsException("SIGN_UPS_PAUSED", "Too many sign-ups today; try again tomorrow");
        }

        // Barbers need a way to reach customers (running late, a cancellation), and the admin barbers.
        request.setPhoneNumber(PhoneNumbers.normalize(request.getPhoneNumber()));

        UserResponseDTO response = userService.saveUser(request, false);

        User user = userRepository.getReferenceById(response.getId());
        user.setTermsAcceptedAt(clock.instant());
        emailVerificationService.sendVerification(user);

        return response;
    }

    public LoginResponseDTO login(LoginRequestDTO request) {

        User user = userRepository.findByEmail(UserService.normalizeEmail(request.getEmail()))
                .orElseThrow(() -> new UnauthorizedException(INVALID_CREDENTIALS));

        if (!passwordEncoder.matches(
                request.getPassword(),
                user.getPassword()
        )) {
            throw new UnauthorizedException(INVALID_CREDENTIALS);
        }

        // Checked after the password so the verification state is only revealed to the account owner.
        if (!user.isEmailVerified()) {
            throw new EmailNotVerifiedException("Please verify your email before logging in");
        }

        String token = jwtService.generateToken(user);

        return new LoginResponseDTO(token);
    }

}
