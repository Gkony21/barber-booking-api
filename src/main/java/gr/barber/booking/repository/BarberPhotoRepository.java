package gr.barber.booking.repository;

import gr.barber.booking.model.BarberPhoto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BarberPhotoRepository extends JpaRepository<BarberPhoto, Long> {

    List<BarberPhoto> findAllByBarberIdOrderByCreatedAtDescIdDesc(Long barberId);

    long countByBarberId(Long barberId);

    Optional<BarberPhoto> findByIdAndBarberId(Long id, Long barberId);
}
