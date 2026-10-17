package gr.barber.booking.model;

public enum InvitationStatus {
    PENDING,
    ACCEPTED,
    DECLINED,
    // Withdrawn by the shop owner, or dropped because the barber joined another shop.
    CANCELLED
}
