package gr.barber.booking.repository;

import gr.barber.booking.model.InvitationStatus;
import gr.barber.booking.model.ShopInvitation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ShopInvitationRepository extends JpaRepository<ShopInvitation, Long> {

    List<ShopInvitation> findAllByShopIdAndStatusOrderByCreatedAt(Long shopId, InvitationStatus status);

    List<ShopInvitation> findAllByBarberIdAndStatusOrderByCreatedAt(Long barberId, InvitationStatus status);

    boolean existsByShopIdAndBarberIdAndStatus(Long shopId, Long barberId, InvitationStatus status);

    Optional<ShopInvitation> findByIdAndShopId(Long id, Long shopId);

    Optional<ShopInvitation> findByIdAndBarberId(Long id, Long barberId);
}
