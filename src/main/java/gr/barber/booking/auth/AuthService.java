package gr.barber.booking.auth;

import gr.barber.booking.exception.BadRequestException;
import gr.barber.booking.exception.UnauthorizedException;
import gr.barber.booking.model.Role;
import org.springframework.stereotype.Service;
import gr.barber.booking.dto.UserRequestDTO;
import gr.barber.booking.dto.UserResponseDTO;
import gr.barber.booking.service.UserService;
import gr.barber.booking.dto.LoginRequestDTO;
import gr.barber.booking.dto.LoginResponseDTO;
import gr.barber.booking.model.User;
import gr.barber.booking.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import gr.barber.booking.security.JwtService;

@Service
public class AuthService {

    // Same message for unknown email and wrong password, so login cannot be used to discover accounts.
    private static final String INVALID_CREDENTIALS = "Invalid email or password";

    private final UserService userService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserService userService,
                       UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService) {

        this.userService = userService;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public UserResponseDTO register(UserRequestDTO request) {

        if (request.getRole() != Role.CUSTOMER && request.getRole() != Role.BARBER) {
            throw new BadRequestException("Invalid role. Allowed roles: CUSTOMER, BARBER");
        }

        return userService.saveUser(request);
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

        String token = jwtService.generateToken(user.getEmail(), user.getRole().name());

        return new LoginResponseDTO(token);
    }

}
