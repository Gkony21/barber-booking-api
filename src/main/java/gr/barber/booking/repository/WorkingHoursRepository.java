package gr.barber.booking.repository;

import gr.barber.booking.model.WorkingHours;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface WorkingHoursRepository extends JpaRepository<WorkingHours, Long> {

    List<WorkingHours> findAllByBarberId(Long barberId);

    @Modifying
    @Query("delete from WorkingHours w where w.barber.id = :barberId")
    void deleteAllByBarberId(Long barberId);
}
