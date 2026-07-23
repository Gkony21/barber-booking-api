package gr.barber.booking.auth;

import gr.barber.booking.dto.UserRequestDTO;
import gr.barber.booking.dto.UserResponseDTO;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public UserResponseDTO register(@RequestBody UserRequestDTO request) {
        return authService.register(request);
    }

}