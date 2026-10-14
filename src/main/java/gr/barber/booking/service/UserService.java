package gr.barber.booking.service;

import gr.barber.booking.model.User;
import gr.barber.booking.repository.UserRepository;
import gr.barber.booking.auth.EmailVerificationService;
import gr.barber.booking.auth.PasswordPolicy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import gr.barber.booking.dto.UserRequestDTO;
import gr.barber.booking.dto.UserResponseDTO;
import java.util.List;
import java.util.Locale;
import gr.barber.booking.exception.ConflictException;
import gr.barber.booking.exception.UserNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;

@Service

public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailVerificationService emailVerificationService;
    private final PasswordPolicy passwordPolicy;

    public UserService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       EmailVerificationService emailVerificationService,
                       PasswordPolicy passwordPolicy) {

        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailVerificationService = emailVerificationService;
        this.passwordPolicy = passwordPolicy;
    }

    // Accounts created by an admin are trusted; self-registered ones must verify their email.
    public UserResponseDTO saveUser(UserRequestDTO request, boolean emailVerified) {

        String email = normalizeEmail(request.getEmail());
        if (userRepository.findByEmail(email).isPresent()) {
            throw new ConflictException("Email is already registered");
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

    public UserResponseDTO getUserById(Long id) {

        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        return new UserResponseDTO(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getPhoneNumber(),
                user.getRole()
        );
    }

    @Transactional
    public UserResponseDTO updateUser(Long id, UserRequestDTO request) {

        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        String email = normalizeEmail(request.getEmail());
        boolean emailChanged = !email.equals(user.getEmail());
        if (emailChanged && userRepository.findByEmail(email).isPresent()) {
            throw new ConflictException("Email is already registered");
        }

        user.setName(request.getName());
        user.setEmail(email);
        if (emailChanged) {
            // The new address has to be confirmed before it can be used to log in.
            user.setEmailVerified(false);
            emailVerificationService.sendVerification(user);
        }
        user.setPhoneNumber(request.getPhoneNumber());
        passwordPolicy.check(request.getPassword(), email);
        user.setPassword(passwordEncoder.encode(request.getPassword()));


        User updatedUser = userRepository.save(user);

        return new UserResponseDTO(
                updatedUser.getId(),
                updatedUser.getName(),
                updatedUser.getEmail(),
                updatedUser.getPhoneNumber(),
                updatedUser.getRole()
        );
    }

    public void deleteUser(Long id) {

        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        userRepository.delete(user);
    }

    public static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
