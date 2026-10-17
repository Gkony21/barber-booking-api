package gr.barber.booking.repository;

import org.springframework.data.jpa.repository.Query;
import java.util.Collection;
import gr.barber.booking.model.BarberPhoto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BarberPhotoRepository extends JpaRepository<BarberPhoto, Long> {

    List<BarberPhoto> findAllByBarberIdOrderByCreatedAtDescIdDesc(Long barberId);

    long countByBarberId(Long barberId);

    // [barberId, imageKey] for every photo of these barbers, newest first.
    @Query("""
            select p.barber.id, p.imageKey from BarberPhoto p
            where p.barber.id in :barberIds
            order by p.createdAt desc, p.id desc
            """)
    List<Object[]> findKeysNewestFirst(Collection<Long> barberIds);

    Optional<BarberPhoto> findTopByBarberIdOrderByCreatedAtDescIdDesc(Long barberId);

    Optional<BarberPhoto> findByIdAndBarberId(Long id, Long barberId);
}
