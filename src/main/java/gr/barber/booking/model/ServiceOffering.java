package gr.barber.booking.model;

import jakarta.persistence.*;

import java.math.BigDecimal;

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

    // Deactivated instead of deleted so appointments can keep referencing it.
    @Column(nullable = false)
    private boolean active = true;

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

    public boolean isMain() {
        return kind == ServiceKind.MAIN;
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
