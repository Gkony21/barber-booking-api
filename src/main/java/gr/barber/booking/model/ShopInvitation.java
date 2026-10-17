package gr.barber.booking.model;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/** A shop owner's invitation for a barber to join the shop; the barber accepts or declines it. */
@Entity
@Table(name = "shop_invitations")
public class ShopInvitation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shop_id", nullable = false)
    private Shop shop;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "barber_id", nullable = false)
    private Barber barber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InvitationStatus status = InvitationStatus.PENDING;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant respondedAt;

    protected ShopInvitation() {
    }

    public ShopInvitation(Shop shop, Barber barber) {
        this.shop = shop;
        this.barber = barber;
    }

    public void close(InvitationStatus status, Instant at) {
        this.status = status;
        this.respondedAt = at;
    }

    public Long getId() {
        return id;
    }

    public Shop getShop() {
        return shop;
    }

    public Barber getBarber() {
        return barber;
    }

    public InvitationStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
