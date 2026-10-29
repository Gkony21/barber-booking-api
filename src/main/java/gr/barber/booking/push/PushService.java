package gr.barber.booking.push;

import gr.barber.booking.exception.BadRequestException;
import gr.barber.booking.exception.ResourceNotFoundException;
import gr.barber.booking.model.User;
import gr.barber.booking.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.List;

/**
 * Phone notifications: which browsers each user has turned them on in, and sending to all of them.
 * A browser that has been unsubscribed or uninstalled is forgotten the first time a send fails for it.
 */
@Service
public class PushService {

    private static final Logger log = LoggerFactory.getLogger(PushService.class);

    private final PushSubscriptionRepository subscriptions;
    private final UserRepository userRepository;
    private final WebPushSender sender;
    private final PushProperties properties;

    public PushService(PushSubscriptionRepository subscriptions, UserRepository userRepository,
                       WebPushSender sender, PushProperties properties) {
        this.subscriptions = subscriptions;
        this.userRepository = userRepository;
        this.sender = sender;
        this.properties = properties;
    }

    @PostConstruct
    void checkKeys() {
        if (!properties.enabled()) {
            log.info("Phone notifications are off: set VAPID_PUBLIC_KEY and VAPID_PRIVATE_KEY to turn them on");
        } else if (!sender.keysMatch()) {
            log.error("VAPID_PUBLIC_KEY and VAPID_PRIVATE_KEY do not belong together; notifications will be refused");
        }
    }

    public boolean enabled() {
        return properties.enabled();
    }

    public String publicKey() {
        if (!enabled()) {
            throw new ResourceNotFoundException("Phone notifications are not set up");
        }
        return properties.publicKey();
    }

    @Transactional
    public void subscribe(String email, PushSubscriptionRequestDTO request) {
        if (!enabled()) {
            throw new BadRequestException("PUSH_OFF", "Phone notifications are not set up");
        }
        User user = userRepository.findByEmail(email).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        PushSubscription subscription = subscriptions.findByEndpoint(request.endpoint())
                .orElseGet(() -> new PushSubscription(user, request.endpoint(), request.keys().p256dh(), request.keys().auth()));
        subscription.update(user, request.keys().p256dh(), request.keys().auth());
        subscriptions.save(subscription);
    }

    @Transactional
    public void unsubscribe(String email, String endpoint) {
        subscriptions.deleteByEndpointAndUserEmail(endpoint, email);
    }

    /**
     * Sends to every browser where the user turned notifications on; quietly does nothing when there
     * are none or notifications are off. A notification failing never affects what caused it.
     *
     * @return how many browsers it reached
     */
    @Transactional
    public int notifyUser(String email, PushMessage message) {
        if (!enabled()) {
            return 0;
        }
        int delivered = 0;
        for (PushSubscription subscription : List.copyOf(subscriptions.findAllByUserEmail(email))) {
            try {
                int status = sender.send(subscription.getEndpoint(), subscription.getP256dh(), subscription.getAuth(), message.json());
                if (status == 404 || status == 410) {
                    subscriptions.delete(subscription); // unsubscribed or the app was removed
                } else if (status >= 200 && status < 300) {
                    delivered++;
                } else {
                    log.warn("Push service answered {} for subscription {}", status, subscription.getId());
                }
            } catch (IOException e) {
                log.warn("Could not send a notification to subscription {}", subscription.getId(), e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return delivered;
            }
        }
        return delivered;
    }
}
