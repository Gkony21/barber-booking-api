package gr.barber.booking.controller;

import gr.barber.booking.dto.DeleteAccountRequestDTO;
import gr.barber.booking.service.AccountService;
import gr.barber.booking.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;
import gr.barber.booking.dto.UserRequestDTO;
import gr.barber.booking.dto.UserResponseDTO;
import java.util.List;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;
    private final AccountService accountService;

    public UserController(UserService userService, AccountService accountService) {
        this.userService = userService;
        this.accountService = accountService;
    }

    @PostMapping
    public UserResponseDTO createUser(@RequestBody UserRequestDTO request) {
        return userService.saveUser(request, true);
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

    @GetMapping("/{id}")
    public UserResponseDTO getUserById(@PathVariable Long id, Authentication authentication) {
        UserResponseDTO user = userService.getUserById(id);
        authorizeAccess(user, authentication);
        return user;
    }

    @PutMapping("/{id}")
    public UserResponseDTO updateUser(
            @PathVariable Long id,
            @RequestBody UserRequestDTO request,
            Authentication authentication) {

        UserResponseDTO existing = userService.getUserById(id);
        authorizeAccess(existing, authentication);
        return userService.updateUser(id, request);
    }

    @DeleteMapping("/{id}")
    public void deleteUser(@PathVariable Long id, Authentication authentication) {
        UserResponseDTO existing = userService.getUserById(id);
        authorizeAccess(existing, authentication);
        userService.deleteUser(id);
    }

    private void authorizeAccess(UserResponseDTO targetUser, Authentication authentication) {
        boolean isOwner = authentication.getName().equals(targetUser.getEmail());
        boolean isAdmin = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(role -> role.equals("ROLE_ADMIN"));

        if (!isOwner && !isAdmin) {
            throw new AccessDeniedException("You do not have permission to access this resource");
        }
    }
}