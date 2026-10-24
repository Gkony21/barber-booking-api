package gr.barber.booking.repository;

import gr.barber.booking.model.ClientNote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ClientNoteRepository extends JpaRepository<ClientNote, Long> {

    List<ClientNote> findAllByBarberId(Long barberId);

    Optional<ClientNote> findByBarberIdAndClientKey(Long barberId, String clientKey);
}
