package gr.barber.booking.repository;

import gr.barber.booking.model.ServiceOffering;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ServiceOfferingRepository extends JpaRepository<ServiceOffering, Long> {

    List<ServiceOffering> findAllByBarberIdOrderByName(Long barberId);

    List<ServiceOffering> findAllByBarberIdAndActiveTrueOrderByName(Long barberId);

    Optional<ServiceOffering> findByIdAndBarberId(Long id, Long barberId);
}
