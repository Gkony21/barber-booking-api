package gr.barber.booking.model;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalTime;

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

    // Set for a block within a single day (startDate == endDate); null for whole days off.
    private LocalTime startTime;

    private LocalTime endTime;

    protected TimeOff() {
    }

    public TimeOff(Barber barber, LocalDate startDate, LocalDate endDate, String reason) {
        this.barber = barber;
        this.startDate = startDate;
        this.endDate = endDate;
        this.reason = reason;
    }

    /** A few hours on one day, e.g. 12:00-14:00. */
    public static TimeOff block(Barber barber, LocalDate date, LocalTime start, LocalTime end, String reason) {
        TimeOff block = new TimeOff(barber, date, date, reason);
        block.startTime = start;
        block.endTime = end;
        return block;
    }

    public boolean isWholeDays() {
        return startTime == null;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
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
