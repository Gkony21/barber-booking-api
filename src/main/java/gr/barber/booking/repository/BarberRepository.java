package gr.barber.booking.repository;

import gr.barber.booking.model.Barber;
import gr.barber.booking.model.BarberStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface BarberRepository extends JpaRepository<Barber, Long> {

    Optional<Barber> findByUserEmail(String email);

    boolean existsByUserEmail(String email);

    Optional<Barber> findByIdAndStatus(Long id, BarberStatus status);

    List<Barber> findAllByStatus(BarberStatus status);

    List<Barber> findAllByStatusAndShopId(BarberStatus status, Long shopId);

    @Query("""
            select b from Barber b left join b.shop s
            where b.status = :status
              and lower(coalesce(s.city, b.city)) = lower(:city)
            """)
    List<Barber> findAllByStatusAndCity(BarberStatus status, String city);
}
