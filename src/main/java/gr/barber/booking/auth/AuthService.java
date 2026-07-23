package gr.barber.booking.auth;

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
import java.util.Optional;

@Service
public class AuthService {

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
        return userService.saveUser(request);
    }

    public LoginResponseDTO login(LoginRequestDTO request) {

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!passwordEncoder.matches(
                request.getPassword(),
                user.getPassword()
        )) {
            throw new RuntimeException("Wrong password");
        }

        String token = jwtService.generateToken(user.getEmail());

        return new LoginResponseDTO(token);
    }

}