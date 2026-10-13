package gr.barber.booking.model;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "appointments")
public class Appointment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private User customer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "barber_id", nullable = false)
    private Barber barber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_id", nullable = false)
    private ServiceOffering service;

    // Snapshot of the service at booking time.
    @Column(nullable = false)
    private String serviceName;

    @Column(nullable = false, precision = 8, scale = 2)
    private BigDecimal price;

    @Column(nullable = false)
    private Instant startAt;

    @Column(nullable = false)
    private Instant endAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AppointmentStatus status;

    @Enumerated(EnumType.STRING)
    private CancelledBy cancelledBy;

    private String customerNote;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected Appointment() {
    }

    public Appointment(User customer, Barber barber, ServiceOffering service,
                       Instant startAt, Instant endAt, AppointmentStatus status, String customerNote) {
        this.customer = customer;
        this.barber = barber;
        this.service = service;
        this.serviceName = service.getName();
        this.price = service.getPrice();
        this.startAt = startAt;
        this.endAt = endAt;
        this.status = status;
        this.customerNote = customerNote;
    }

    public void confirm() {
        this.status = AppointmentStatus.CONFIRMED;
    }

    public void reject() {
        this.status = AppointmentStatus.REJECTED;
    }

    public void cancel(CancelledBy by) {
        this.status = AppointmentStatus.CANCELLED;
        this.cancelledBy = by;
    }

    public Long getId() {
        return id;
    }

    public User getCustomer() {
        return customer;
    }

    public Barber getBarber() {
        return barber;
    }

    public ServiceOffering getService() {
        return service;
    }

    public String getServiceName() {
        return serviceName;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public Instant getStartAt() {
        return startAt;
    }

    public Instant getEndAt() {
        return endAt;
    }

    public AppointmentStatus getStatus() {
        return status;
    }

    public CancelledBy getCancelledBy() {
        return cancelledBy;
    }

    public String getCustomerNote() {
        return customerNote;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
