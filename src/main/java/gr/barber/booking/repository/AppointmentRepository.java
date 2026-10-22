package gr.barber.booking.repository;

import java.util.Collection;
import gr.barber.booking.model.Appointment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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
            select a from Appointment a
            where a.barber.id in :barberIds
              and a.status in (gr.barber.booking.model.AppointmentStatus.PENDING,
                               gr.barber.booking.model.AppointmentStatus.CONFIRMED)
              and a.startAt < :to and a.endAt > :from
            """)
    List<Appointment> findActiveForBarbersOverlapping(Collection<Long> barberIds, Instant from, Instant to);

    @Query("""
            select count(a) > 0 from Appointment a
            where a.customer.id = :customerId
              and a.status in (gr.barber.booking.model.AppointmentStatus.PENDING,
                               gr.barber.booking.model.AppointmentStatus.CONFIRMED)
              and a.startAt < :to and a.endAt > :from
            """)
    boolean customerHasActiveOverlapping(Long customerId, Instant from, Instant to);

    @Query("""
            select count(a) from Appointment a
            where a.customer.id = :customerId
              and a.status in (gr.barber.booking.model.AppointmentStatus.PENDING,
                               gr.barber.booking.model.AppointmentStatus.CONFIRMED)
              and a.startAt > :now and a.replaces is null
            """)
    long countUpcomingActiveForCustomer(Long customerId, Instant now);

    @Query("""
            select count(a) > 0 from Appointment a left join a.replaces r
            where a.customer.id = :customerId and a.id <> :exceptId and (r is null or r.id <> :exceptId)
              and a.status in (gr.barber.booking.model.AppointmentStatus.PENDING,
                               gr.barber.booking.model.AppointmentStatus.CONFIRMED)
              and a.startAt < :to and a.endAt > :from
            """)
    boolean customerHasOtherActiveOverlapping(Long customerId, Long exceptId, Instant from, Instant to);

    // [customerId, how many times they did not turn up]
    @Query("""
            select a.customer.id, count(a) from Appointment a
            where a.customer.id in :customerIds and a.status = gr.barber.booking.model.AppointmentStatus.NO_SHOW
            group by a.customer.id
            """)
    List<Object[]> countNoShows(Collection<Long> customerIds);

    List<Appointment> findAllByCustomerIdOrderByStartAtDesc(Long customerId);

    List<Appointment> findAllByBarberIdAndStartAtGreaterThanEqualAndStartAtLessThanOrderByStartAt(Long barberId,
                                                                                                  Instant from,
                                                                                                  Instant to);

    Optional<Appointment> findByIdAndCustomerId(Long id, Long customerId);

    Optional<Appointment> findByIdAndBarberId(Long id, Long barberId);

    @Query("""
            select a from Appointment a
            where a.status = gr.barber.booking.model.AppointmentStatus.CONFIRMED
              and a.customer is not null
              and a.startAt > :from and a.startAt <= :to
            """)
    List<Appointment> findConfirmedStartingBetween(Instant from, Instant to);

    // "Claim" a reminder: only one caller gets 1 back, so each reminder is sent once.
    @Modifying
    @Query("update Appointment a set a.reminderDaySentAt = :now where a.id = :id and a.reminderDaySentAt is null")
    int claimDayReminder(Long id, Instant now);

    // A change also closes before the appointment it would move, so the customer knows which time holds.
    @Query("""
            select a from Appointment a left join a.replaces r
            where a.status = gr.barber.booking.model.AppointmentStatus.PENDING
              and (a.startAt <= :before or r.startAt <= :before)
            """)
    List<Appointment> findPendingStartingBefore(Instant before);

    Optional<Appointment> findByReplacesId(Long originalId);

    List<Appointment> findAllByReplacesIdIn(Collection<Long> originalIds);

    @Query("""
            select a from Appointment a
            where a.status = gr.barber.booking.model.AppointmentStatus.PENDING
              and a.createdAt <= :before and a.requestReminderSentAt is null
            """)
    List<Appointment> findPendingCreatedBefore(Instant before);

    @Modifying
    @Query("update Appointment a set a.requestReminderSentAt = :now where a.id = :id and a.requestReminderSentAt is null")
    int claimRequestReminder(Long id, Instant now);

    @Modifying
    @Query("update Appointment a set a.reminderSoonSentAt = :now where a.id = :id and a.reminderSoonSentAt is null")
    int claimSoonReminder(Long id, Instant now);

    @Query("""
            select a from Appointment a left join a.customer c
            where (c.id = :userId or a.barber.user.id = :userId)
              and a.status in (gr.barber.booking.model.AppointmentStatus.PENDING,
                               gr.barber.booking.model.AppointmentStatus.CONFIRMED)
              and a.startAt > :now
            """)
    List<Appointment> findUpcomingActiveInvolving(Long userId, Instant now);
}
