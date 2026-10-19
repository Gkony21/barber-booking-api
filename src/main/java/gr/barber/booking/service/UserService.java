package gr.barber.booking.service;

import gr.barber.booking.model.User;
import gr.barber.booking.repository.UserRepository;
import gr.barber.booking.auth.PasswordPolicy;
import org.springframework.stereotype.Service;
import gr.barber.booking.dto.UserRequestDTO;
import gr.barber.booking.dto.UserResponseDTO;
import java.util.List;
import java.util.Locale;
import gr.barber.booking.exception.BadRequestException;
import gr.barber.booking.exception.ConflictException;
import gr.barber.booking.exception.ResourceNotFoundException;
import gr.barber.booking.dto.UpdateMeRequestDTO;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;

@Service

public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;

    public UserService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       PasswordPolicy passwordPolicy) {

        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
    }

    // Accounts created by an admin are trusted; self-registered ones must verify their email.
    public UserResponseDTO saveUser(UserRequestDTO request, boolean emailVerified) {

        String email = normalizeEmail(request.getEmail());
        if (userRepository.findByEmail(email).isPresent()) {
            throw new ConflictException("EMAIL_TAKEN", "Email is already registered");
        }
        passwordPolicy.check(request.getPassword(), email);

        User user = new User(
                request.getName(),
                email,
                request.getPhoneNumber(),
                passwordEncoder.encode(request.getPassword()),
                request.getRole()
        );
        user.setEmailVerified(emailVerified);

        User savedUser = userRepository.save(user);

        return new UserResponseDTO(
                savedUser.getId(),
                savedUser.getName(),
                savedUser.getEmail(),
                savedUser.getPhoneNumber(),
                savedUser.getRole()
        );
    }

    public List<UserResponseDTO> getAllUsers() {

        return userRepository.findAll()
                .stream()
                .map(user -> new UserResponseDTO(
                        user.getId(),
                        user.getName(),
                        user.getEmail(),
                        user.getPhoneNumber(),
                        user.getRole()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public UserResponseDTO getMe(String email) {
        return toResponse(getByEmail(email));
    }

    @Transactional
    public UserResponseDTO updateMe(String email, UpdateMeRequestDTO request) {
        User user = getByEmail(email);
        user.setName(request.name().strip());
        user.setPhoneNumber(PhoneNumbers.normalize(request.phoneNumber()));
        return toResponse(user);
    }

    /**
     * Changes the password and signs out every other device; returns a fresh token so this one
     * stays signed in.
     */
    @Transactional
    public User changePassword(String email, String currentPassword, String newPassword) {
        User user = getByEmail(email);
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new BadRequestException("WRONG_PASSWORD", "Wrong password");
        }
        passwordPolicy.check(newPassword, user.getEmail());
        user.setPassword(passwordEncoder.encode(newPassword));
        user.invalidateTokens();
        return user;
    }

    private User getByEmail(String email) {
        return userRepository.findByEmail(email).orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private static UserResponseDTO toResponse(User user) {
        return new UserResponseDTO(user.getId(), user.getName(), user.getEmail(), user.getPhoneNumber(), user.getRole());
    }

    public static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
