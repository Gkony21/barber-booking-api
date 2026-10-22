package gr.barber.booking.repository;

import gr.barber.booking.model.Role;
import gr.barber.booking.model.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    List<User> findAllByRole(Role role);

    long countByCreatedAtAfter(Instant after);

    // Accounts whose email was never confirmed; their barber profile and tokens go with them (ON DELETE CASCADE).
    @Modifying
    @Query("delete from User u where u.emailVerified = false and u.createdAt < :before")
    int deleteUnverifiedCreatedBefore(Instant before);

    // Locks the row until the transaction ends, so two bookings by the same person run one after
    // the other and both see the other's appointment when counting.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.email = :email")
    Optional<User> findByEmailForUpdate(String email);

}