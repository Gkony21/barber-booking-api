package gr.barber.booking.service;

import gr.barber.booking.config.BookingProperties;
import gr.barber.booking.exception.BadRequestException;
import gr.barber.booking.exception.ResourceNotFoundException;
import gr.barber.booking.media.ImageStorage;
import gr.barber.booking.model.Appointment;
import gr.barber.booking.model.CancelledBy;
import gr.barber.booking.model.User;
import gr.barber.booking.notification.AppointmentEvent;
import gr.barber.booking.notification.AppointmentEventType;
import gr.barber.booking.repository.AppointmentRepository;
import gr.barber.booking.repository.BarberPhotoRepository;
import gr.barber.booking.repository.BarberRepository;
import gr.barber.booking.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Deletes an account and everything tied to it (GDPR "right to erasure"). Upcoming appointments
 * are cancelled first so the other side is emailed, and photos are removed from storage.
 */
@Service
public class AccountService {

    private final UserRepository userRepository;
    private final BarberRepository barberRepository;
    private final BarberPhotoRepository photoRepository;
    private final AppointmentRepository appointmentRepository;
    private final ImageStorage storage;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;
    private final BookingProperties bookingProperties;
    private final EntityManager entityManager;
    private final Clock clock;

    public AccountService(UserRepository userRepository,
                          BarberRepository barberRepository,
                          BarberPhotoRepository photoRepository,
                          AppointmentRepository appointmentRepository,
                          ImageStorage storage,
                          PasswordEncoder passwordEncoder,
                          ApplicationEventPublisher eventPublisher,
                          BookingProperties bookingProperties,
                          EntityManager entityManager,
                          Clock clock) {
        this.userRepository = userRepository;
        this.barberRepository = barberRepository;
        this.photoRepository = photoRepository;
        this.appointmentRepository = appointmentRepository;
        this.storage = storage;
        this.passwordEncoder = passwordEncoder;
        this.eventPublisher = eventPublisher;
        this.bookingProperties = bookingProperties;
        this.entityManager = entityManager;
        this.clock = clock;
    }

    @Transactional
    public void deleteAccount(String email, String password) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new BadRequestException("WRONG_PASSWORD", "Wrong password");
        }

        for (Appointment appointment : appointmentRepository.findUpcomingActiveInvolving(user.getId(), clock.instant())) {
            boolean asCustomer = appointment.getCustomer() != null
                    && Objects.equals(appointment.getCustomer().getId(), user.getId());
            appointment.cancel(asCustomer ? CancelledBy.CUSTOMER : CancelledBy.BARBER);
            eventPublisher.publishEvent(AppointmentEvent.of(
                    asCustomer ? AppointmentEventType.CANCELLED_BY_CUSTOMER : AppointmentEventType.CANCELLED_BY_BARBER,
                    appointment, bookingProperties.timeZone()));
        }

        List<String> imageKeys = new ArrayList<>();
        barberRepository.findByUserEmail(email).ifPresent(barber -> {
            if (barber.getAvatarKey() != null) {
                imageKeys.add(barber.getAvatarKey());
            }
            if (barber.getCoverKey() != null) {
                imageKeys.add(barber.getCoverKey());
            }
            photoRepository.findAllByBarberIdOrderByCreatedAtDescIdDesc(barber.getId())
                    .forEach(photo -> imageKeys.add(photo.getImageKey()));
        });

        // The database removes everything that belongs to the user (barber profile, shop they own,
        // services, hours, photos, appointments, tokens) through ON DELETE CASCADE.
        entityManager.flush();
        entityManager.clear();
        userRepository.deleteById(user.getId());

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                imageKeys.forEach(storage::delete);
            }
        });
    }
}
