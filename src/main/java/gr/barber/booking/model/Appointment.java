package gr.barber.booking.model;

import jakarta.persistence.*;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "appointments")
public class Appointment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Null for appointments the barber entered for a phone or walk-in customer (see guestName).
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private User customer;

    private String guestName;

    private String guestPhone;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "barber_id", nullable = false)
    private Barber barber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_id", nullable = false)
    private ServiceOffering service;

    // Snapshot of the main service at booking time; price is the total including extras.
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

    // Chosen on top of the main service; included in price and endAt.
    @OneToMany(mappedBy = "appointment", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position")
    @BatchSize(size = 50)
    private List<AppointmentExtra> extras = new ArrayList<>();

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant reminderDaySentAt;

    private Instant reminderSoonSentAt;

    private Instant requestReminderSentAt;

    // Set on a request to move a confirmed appointment: this pending row holds the new time, while
    // the confirmed one keeps its own until the barber answers.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "replaces_id")
    private Appointment replaces;

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

    /** An appointment the barber enters for someone without an account (phone call, walk-in). */
    public static Appointment manual(Barber barber, ServiceOffering service, Instant startAt, Instant endAt,
                                     String guestName, String guestPhone, String note) {
        Appointment appointment = new Appointment(null, barber, service, startAt, endAt, AppointmentStatus.CONFIRMED, note);
        appointment.guestName = guestName;
        appointment.guestPhone = guestPhone;
        return appointment;
    }

    /** A request to move a confirmed appointment to a new time, with the same services and price. */
    public static Appointment changeOf(Appointment original, Instant startAt, Instant endAt) {
        Appointment change = new Appointment(original.customer, original.barber, original.service,
                startAt, endAt, AppointmentStatus.PENDING, original.customerNote);
        change.serviceName = original.serviceName;
        change.price = original.price;
        original.extras.forEach(extra -> change.extras.add(new AppointmentExtra(change, extra)));
        change.replaces = original;
        return change;
    }

    /** Adds the extras (in order) and their prices to the total. */
    public void addExtras(List<ServiceOffering> chosen) {
        for (ServiceOffering extra : chosen) {
            extras.add(new AppointmentExtra(this, extra, extras.size()));
            price = price.add(extra.getPrice());
        }
    }

    public List<AppointmentExtra> getExtras() {
        return extras;
    }

    public boolean isChange() {
        return replaces != null;
    }

    /** For a change: the confirmed appointment it would move. */
    public Appointment getReplaces() {
        return replaces;
    }

    public boolean isManual() {
        return customer == null;
    }

    /** Name to show for whoever the appointment is for. */
    public String getCustomerName() {
        return customer != null ? customer.getName() : guestName;
    }

    public String getCustomerPhone() {
        return customer != null ? customer.getPhoneNumber() : guestPhone;
    }

    public void confirm() {
        this.status = AppointmentStatus.CONFIRMED;
    }

    public void reject() {
        this.status = AppointmentStatus.REJECTED;
    }

    public void markNoShow() {
        this.status = AppointmentStatus.NO_SHOW;
    }

    /**
     * Moves the appointment to a new time; reminders start over. A barber who approves bookings by
     * hand has to answer again.
     */
    public void moveTo(Instant newStart, Instant newEnd, AppointmentStatus newStatus) {
        this.startAt = newStart;
        this.endAt = newEnd;
        this.status = newStatus;
        this.reminderDaySentAt = null;
        this.reminderSoonSentAt = null;
        this.requestReminderSentAt = null;
    }

    /**
     * The barber's change: new start and end (reminders start over only if the time moved) and the
     * services, whose names and prices are copied again as they are now.
     */
    public void changeByBarber(Instant newStart, Instant newEnd, ServiceOffering main, List<ServiceOffering> chosenExtras) {
        if (!newStart.equals(startAt)) {
            this.reminderDaySentAt = null;
            this.reminderSoonSentAt = null;
        }
        this.startAt = newStart;
        this.endAt = newEnd;
        this.service = main;
        this.serviceName = main.getName();
        this.price = main.getPrice();
        this.extras.clear();
        addExtras(chosenExtras);
    }

    /** For an appointment the barber entered: who it is for and the barber's note. */
    public void changeGuest(String name, String phone, String note) {
        this.guestName = name;
        this.guestPhone = phone;
        this.customerNote = note;
    }

    /** A request nobody answered in time. */
    public void expire() {
        this.status = AppointmentStatus.EXPIRED;
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
