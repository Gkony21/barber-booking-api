package gr.barber.booking.auth;

import gr.barber.booking.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;

/**
 * Removes accounts whose email was never confirmed, so fake sign-ups do not pile up in the
 * database and the address can be used to sign up again.
 */
@Service
public class UnverifiedAccountCleanup {

    static final Duration KEEP_FOR = Duration.ofHours(48);

    private static final Logger log = LoggerFactory.getLogger(UnverifiedAccountCleanup.class);

    private final UserRepository userRepository;
    private final Clock clock;

    public UnverifiedAccountCleanup(UserRepository userRepository, Clock clock) {
        this.userRepository = userRepository;
        this.clock = clock;
    }

    @Transactional
    public int process() {
        int removed = userRepository.deleteUnverifiedCreatedBefore(clock.instant().minus(KEEP_FOR));
        if (removed > 0) {
            log.info("Removed {} unverified accounts", removed);
        }
        return removed;
    }
}
