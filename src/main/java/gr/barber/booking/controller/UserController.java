package gr.barber.booking.controller;

import gr.barber.booking.dto.ChangePasswordRequestDTO;
import gr.barber.booking.dto.DeleteAccountRequestDTO;
import gr.barber.booking.dto.LoginResponseDTO;
import gr.barber.booking.dto.UpdateMeRequestDTO;
import gr.barber.booking.model.User;
import gr.barber.booking.security.JwtService;
import gr.barber.booking.service.AccountService;
import gr.barber.booking.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import gr.barber.booking.dto.UserRequestDTO;
import gr.barber.booking.dto.UserResponseDTO;
import java.util.List;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;
    private final AccountService accountService;
    private final JwtService jwtService;

    public UserController(UserService userService, AccountService accountService, JwtService jwtService) {
        this.userService = userService;
        this.accountService = accountService;
        this.jwtService = jwtService;
    }

    @PostMapping
    public UserResponseDTO createUser(@Valid @RequestBody UserRequestDTO request) {
        return userService.saveUser(request, true);
    }

    @GetMapping("/me")
    public UserResponseDTO getMe(Authentication authentication) {
        return userService.getMe(authentication.getName());
    }

    @PutMapping("/me")
    public UserResponseDTO updateMe(@Valid @RequestBody UpdateMeRequestDTO request, Authentication authentication) {
        return userService.updateMe(authentication.getName(), request);
    }

    // Answers with a new token: other devices are signed out, this one stays signed in.
    @PostMapping("/me/password")
    public LoginResponseDTO changePassword(@Valid @RequestBody ChangePasswordRequestDTO request, Authentication authentication) {
        User user = userService.changePassword(authentication.getName(), request.currentPassword(), request.newPassword());
        return new LoginResponseDTO(jwtService.generateToken(user));
    }

    // Deletes the logged-in person's own account and all their data.
    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMyAccount(@Valid @RequestBody DeleteAccountRequestDTO request, Authentication authentication) {
        accountService.deleteAccount(authentication.getName(), request.password());
    }

    @GetMapping
    public List<UserResponseDTO> getAllUsers() {
        return userService.getAllUsers();
    }
}
