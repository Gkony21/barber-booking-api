package gr.barber.booking.repository;

import org.springframework.data.jpa.repository.Query;
import java.util.Collection;
import gr.barber.booking.model.TimeOff;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TimeOffRepository extends JpaRepository<TimeOff, Long> {

    List<TimeOff> findAllByBarberIdAndEndDateGreaterThanEqualOrderByStartDate(Long barberId, LocalDate from);

    Optional<TimeOff> findByIdAndBarberId(Long id, Long barberId);

    // Days off of these barbers that touch the period from..to (both inclusive).
    @Query("""
            select t from TimeOff t
            where t.barber.id in :barberIds and t.startDate <= :to and t.endDate >= :from
            """)
    List<TimeOff> findOverlapping(Collection<Long> barberIds, LocalDate from, LocalDate to);

}
