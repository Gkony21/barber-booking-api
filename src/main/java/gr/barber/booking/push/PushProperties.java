package gr.barber.booking.push;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Keys for phone notifications (Web Push, "VAPID"), from VAPID_PUBLIC_KEY / VAPID_PRIVATE_KEY /
 * VAPID_SUBJECT. Without keys, notifications are off and the app does not offer them.
 *
 * @param publicKey  uncompressed P-256 point, base64url (also given to browsers when they subscribe)
 * @param privateKey the matching 32-byte private value, base64url
 * @param subject    how push services can reach us: an https:// or mailto: address
 */
@ConfigurationProperties("app.push")
public record PushProperties(String publicKey, String privateKey, String subject) {

    public boolean enabled() {
        return publicKey != null && !publicKey.isBlank() && privateKey != null && !privateKey.isBlank();
    }
}
