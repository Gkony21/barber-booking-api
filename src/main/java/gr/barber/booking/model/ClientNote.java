package gr.barber.booking.model;

import jakarta.persistence.*;

import java.time.Instant;

/** A barber's private note about one of their customers (see V21). */
@Entity
@Table(name = "client_notes")
public class ClientNote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "barber_id", nullable = false)
    private Barber barber;

    @Column(nullable = false)
    private String clientKey;

    @Column(nullable = false)
    private String note;

    @Column(nullable = false)
    private Instant updatedAt;

    protected ClientNote() {
    }

    public ClientNote(Barber barber, String clientKey) {
        this.barber = barber;
        this.clientKey = clientKey;
    }

    public void write(String note, Instant at) {
        this.note = note;
        this.updatedAt = at;
    }

    public String getClientKey() {
        return clientKey;
    }

    public String getNote() {
        return note;
    }
}
