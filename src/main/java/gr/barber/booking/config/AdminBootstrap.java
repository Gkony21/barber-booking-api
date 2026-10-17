package gr.barber.booking.config;

import gr.barber.booking.model.Role;
import gr.barber.booking.repository.UserRepository;
import gr.barber.booking.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Makes the accounts listed in app.admin-emails (ADMIN_EMAILS) admins on startup, so the first
 * admin can be set up on any server without database access. The account must be registered and its
 * email verified first; the person logs in again afterwards to get an admin token.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final UserRepository userRepository;
    private final List<String> adminEmails;

    public AdminBootstrap(UserRepository userRepository,
                          @Value("${app.admin-emails:}") List<String> adminEmails) {
        this.userRepository = userRepository;
        this.adminEmails = adminEmails;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (String raw : adminEmails) {
            String email = UserService.normalizeEmail(raw);
            if (email == null || email.isBlank()) {
                continue;
            }
            userRepository.findByEmail(email).ifPresentOrElse(user -> {
                // Only an account whose owner has opened the verification email: otherwise anyone
                // who registered this address first, with their own password, would become admin.
                if (!user.isEmailVerified()) {
                    log.warn("ADMIN_EMAILS lists an account that has not verified its email yet; verify it, then restart");
                } else if (user.getRole() != Role.ADMIN) {
                    user.setRole(Role.ADMIN);
                    userRepository.save(user);
                    log.info("Granted ADMIN role to user {}", user.getId());
                }
            }, () -> log.warn("ADMIN_EMAILS lists an address with no account yet; register it, then restart"));
        }
    }
}
