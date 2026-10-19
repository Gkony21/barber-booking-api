package gr.barber.booking.repository;

import java.util.Collection;
import gr.barber.booking.model.WorkingHours;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface WorkingHoursRepository extends JpaRepository<WorkingHours, Long> {

    List<WorkingHours> findAllByBarberId(Long barberId);

    List<WorkingHours> findAllByBarberIdIn(Collection<Long> barberIds);

    @Query("select distinct w.barber.id from WorkingHours w where w.barber.id in :barberIds")
    List<Long> findBarberIdsWithHours(Collection<Long> barberIds);

    @Modifying
    @Query("delete from WorkingHours w where w.barber.id = :barberId")
    void deleteAllByBarberId(Long barberId);
}
