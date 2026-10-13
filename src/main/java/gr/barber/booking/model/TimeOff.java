package gr.barber.booking.model;

import jakarta.persistence.*;

import java.time.LocalDate;

@Entity
@Table(name = "time_off")
public class TimeOff {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "barber_id", nullable = false)
    private Barber barber;

    @Column(nullable = false)
    private LocalDate startDate;

    // Inclusive.
    @Column(nullable = false)
    private LocalDate endDate;

    private String reason;

    protected TimeOff() {
    }

    public TimeOff(Barber barber, LocalDate startDate, LocalDate endDate, String reason) {
        this.barber = barber;
        this.startDate = startDate;
        this.endDate = endDate;
        this.reason = reason;
    }

    public Long getId() {
        return id;
    }

    public Barber getBarber() {
        return barber;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public String getReason() {
        return reason;
    }
}
