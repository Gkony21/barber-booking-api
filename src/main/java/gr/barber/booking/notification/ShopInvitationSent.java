package gr.barber.booking.notification;

/** Published when a shop owner invites a barber, so the barber hears about it by email. */
public record ShopInvitationSent(String barberEmail, String shopName, String shopCity) {
}
