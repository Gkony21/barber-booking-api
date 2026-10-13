package gr.barber.booking.repository;

import gr.barber.booking.model.Shop;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ShopRepository extends JpaRepository<Shop, Long> {

    List<Shop> findAllByCityIgnoreCase(String city);
}
