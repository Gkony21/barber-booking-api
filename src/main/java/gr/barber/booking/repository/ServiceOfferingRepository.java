package gr.barber.booking.repository;

import java.util.Collection;
import gr.barber.booking.model.ServiceOffering;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ServiceOfferingRepository extends JpaRepository<ServiceOffering, Long> {

    List<ServiceOffering> findAllByBarberIdAndDeletedFalseOrderByName(Long barberId);

    List<ServiceOffering> findAllByBarberIdAndActiveTrueOrderByName(Long barberId);

    List<ServiceOffering> findAllByBarberIdInAndActiveTrue(Collection<Long> barberIds);

    @Query("""
            select distinct s.barber.id from ServiceOffering s
            where s.barber.id in :barberIds and s.active = true and s.kind = gr.barber.booking.model.ServiceKind.MAIN
            """)
    List<Long> findBarberIdsWithMainServices(Collection<Long> barberIds);

    Optional<ServiceOffering> findByIdAndBarberId(Long id, Long barberId);
}
