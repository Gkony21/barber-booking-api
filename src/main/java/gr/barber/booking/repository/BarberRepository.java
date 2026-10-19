package gr.barber.booking.repository;

import gr.barber.booking.model.Barber;
import gr.barber.booking.model.BarberStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public interface BarberRepository extends JpaRepository<Barber, Long> {

    Optional<Barber> findByUserEmail(String email);

    boolean existsByUserEmail(String email);

    Optional<Barber> findBySlug(String slug);

    Optional<Barber> findBySlugAndStatus(String slug, BarberStatus status);

    List<Barber> findAllBySlugIsNull();

    Optional<Barber> findByIdAndStatus(Long id, BarberStatus status);

    // The listing shows each barber's name and shop, so they are loaded in the same query.
    @EntityGraph(attributePaths = {"user", "shop"})
    List<Barber> findAllByStatus(BarberStatus status);

    List<Barber> findAllByStatusOrderByCreatedAtAsc(BarberStatus status);

    // The listing shows each barber's name and shop, so they are loaded in the same query.
    @EntityGraph(attributePaths = {"user", "shop"})
    List<Barber> findAllByStatusAndShopId(BarberStatus status, Long shopId);

    // The listing shows each barber's name and shop, so they are loaded in the same query.
    @EntityGraph(attributePaths = {"user", "shop"})
    @Query("""
            select b from Barber b left join b.shop s
            where b.status = :status
              and lower(function('translate', coalesce(s.city, b.city), :accented, :plain)) like concat('%', :city, '%')
            """)
    List<Barber> findAllByStatusAndCityLike(BarberStatus status, String city, String accented, String plain);

    String ACCENTED = "ΆΈΉΊΌΎΏΪΫάέήίόύώϊϋΐΰς";
    String PLAIN = "ΑΕΗΙΟΥΩΙΥαεηιουωιυιυσ";

    /**
     * Barbers whose (shop's) city contains the search, ignoring accents and case: "αθηνα" and
     * "Αθ" both find "Αθήνα".
     */
    default List<Barber> findAllByStatusAndCity(BarberStatus status, String city) {
        String plain = Normalizer.normalize(city.strip(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.forLanguageTag("el"))
                .replace('ς', 'σ');
        return findAllByStatusAndCityLike(status, plain, ACCENTED, PLAIN);
    }
}
