package gr.barber.booking.repository;

import gr.barber.booking.model.TimeOff;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TimeOffRepository extends JpaRepository<TimeOff, Long> {

    List<TimeOff> findAllByBarberIdAndEndDateGreaterThanEqualOrderByStartDate(Long barberId, LocalDate from);

    Optional<TimeOff> findByIdAndBarberId(Long id, Long barberId);

    boolean existsByBarberIdAndStartDateLessThanEqualAndEndDateGreaterThanEqual(Long barberId,
                                                                                LocalDate date,
                                                                                LocalDate sameDate);

    default boolean isOff(Long barberId, LocalDate date) {
        return existsByBarberIdAndStartDateLessThanEqualAndEndDateGreaterThanEqual(barberId, date, date);
    }
}
