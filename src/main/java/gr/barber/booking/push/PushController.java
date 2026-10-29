package gr.barber.booking.push;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Turning phone notifications on and off in a browser, and a test notification to check them. */
@RestController
@RequestMapping("/push")
public class PushController {

    private final PushService pushService;

    public PushController(PushService pushService) {
        this.pushService = pushService;
    }

    // Public: the browser needs it to subscribe. 404 when notifications are not set up.
    @GetMapping("/key")
    public Map<String, String> publicKey() {
        return Map.of("publicKey", pushService.publicKey());
    }

    @PostMapping("/subscriptions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void subscribe(@Valid @RequestBody PushSubscriptionRequestDTO request, Authentication authentication) {
        pushService.subscribe(authentication.getName(), request);
    }

    public record EndpointDTO(@NotBlank String endpoint) {
    }

    @PostMapping("/unsubscribe")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unsubscribe(@Valid @RequestBody EndpointDTO request, Authentication authentication) {
        pushService.unsubscribe(authentication.getName(), request.endpoint());
    }

    // "Δοκιμή": a notification to every browser of this account, to see that they arrive.
    @PostMapping("/test")
    public Map<String, Integer> test(Authentication authentication) {
        int delivered = pushService.notifyUser(authentication.getName(),
                new PushMessage("Οι ειδοποιήσεις δουλεύουν", "Θα ενημερώνεσαι εδώ για νέα ραντεβού, αιτήματα και ακυρώσεις.", "/barber"));
        return Map.of("delivered", delivered);
    }
}
