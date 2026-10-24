package gr.barber.booking.model;

import jakarta.persistence.*;

import java.math.BigDecimal;

/** An extra chosen with an appointment, with its name, price and length as they were when booked. */
@Entity
@Table(name = "appointment_extras")
public class AppointmentExtra {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "appointment_id", nullable = false)
    private Appointment appointment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_id", nullable = false)
    private ServiceOffering service;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, precision = 8, scale = 2)
    private BigDecimal price;

    @Column(nullable = false)
    private int durationMinutes;

    @Column(nullable = false)
    private int position;

    protected AppointmentExtra() {
    }

    AppointmentExtra(Appointment appointment, ServiceOffering service, int position) {
        this.appointment = appointment;
        this.service = service;
        this.name = service.getName();
        this.price = service.getPrice();
        this.durationMinutes = service.getDurationMinutes();
        this.position = position;
    }

    // The same extra on another appointment (a change of time keeps what was booked).
    AppointmentExtra(Appointment appointment, AppointmentExtra copy) {
        this.appointment = appointment;
        this.service = copy.service;
        this.name = copy.name;
        this.price = copy.price;
        this.durationMinutes = copy.durationMinutes;
        this.position = copy.position;
    }

    public Long getServiceId() {
        return service.getId();
    }

    public String getName() {
        return name;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }
}
