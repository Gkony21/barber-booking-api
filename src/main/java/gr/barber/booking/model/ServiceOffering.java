package gr.barber.booking.model;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Entity
@Table(name = "service_offerings")
public class ServiceOffering {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "barber_id", nullable = false)
    private Barber barber;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(nullable = false)
    private Integer durationMinutes;

    @Column(nullable = false, precision = 8, scale = 2)
    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ServiceKind kind = ServiceKind.MAIN;

    // Deleted by the barber: gone from every list, kept only for appointments that refer to it.
    @Column(nullable = false)
    private boolean deleted = false;

    // Deactivated instead of deleted so appointments can keep referencing it.
    @Column(nullable = false)
    private boolean active = true;

    // Chosen by the barber to show on their card; see headline().
    @Column(nullable = false)
    private boolean featured = false;

    protected ServiceOffering() {
    }

    public ServiceOffering(Barber barber) {
        this.barber = barber;
    }

    public Long getId() {
        return id;
    }

    public Barber getBarber() {
        return barber;
    }

    public ServiceKind getKind() {
        return kind;
    }

    public void setKind(ServiceKind kind) {
        this.kind = kind;
    }

    /** Gone for good from the barber's and customers' lists; booked appointments keep their copy. */
    public void delete() {
        this.deleted = true;
        this.active = false;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public boolean isMain() {
        return kind == ServiceKind.MAIN;
    }

    public boolean isFeatured() {
        return featured;
    }

    public void setFeatured(boolean featured) {
        this.featured = featured;
    }

    /**
     * The service a barber's card shows with its price: the one they chose, or else their first main
     * service (usually the plain haircut). Hidden, deleted and extra services never count.
     */
    public static Optional<ServiceOffering> headline(Collection<ServiceOffering> services) {
        List<ServiceOffering> offered = services.stream().filter(s -> s.isActive() && s.isMain()).toList();
        return offered.stream().filter(ServiceOffering::isFeatured).findFirst()
                .or(() -> offered.stream().min(Comparator.comparing(ServiceOffering::getId)));
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Integer getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(Integer durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
