package gr.barber.booking.repository;

import gr.barber.booking.model.Appointment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    @Query("""
            select a from Appointment a
            where a.barber.id = :barberId
              and a.status in (gr.barber.booking.model.AppointmentStatus.PENDING,
                               gr.barber.booking.model.AppointmentStatus.CONFIRMED)
              and a.startAt < :to and a.endAt > :from
            order by a.startAt
            """)
    List<Appointment> findActiveForBarberOverlapping(Long barberId, Instant from, Instant to);

    @Query("""
            select count(a) > 0 from Appointment a
            where a.customer.id = :customerId
              and a.status in (gr.barber.booking.model.AppointmentStatus.PENDING,
                               gr.barber.booking.model.AppointmentStatus.CONFIRMED)
              and a.startAt < :to and a.endAt > :from
            """)
    boolean customerHasActiveOverlapping(Long customerId, Instant from, Instant to);

    List<Appointment> findAllByCustomerIdOrderByStartAtDesc(Long customerId);

    List<Appointment> findAllByBarberIdAndStartAtGreaterThanEqualAndStartAtLessThanOrderByStartAt(Long barberId,
                                                                                                  Instant from,
                                                                                                  Instant to);

    Optional<Appointment> findByIdAndCustomerId(Long id, Long customerId);

    Optional<Appointment> findByIdAndBarberId(Long id, Long barberId);
}
