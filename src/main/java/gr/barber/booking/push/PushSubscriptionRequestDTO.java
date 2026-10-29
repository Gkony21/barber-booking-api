package gr.barber.booking.push;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** What a browser's PushManager.subscribe() returns (its toJSON()), as sent by the web app. */
public record PushSubscriptionRequestDTO(
        @NotBlank @Size(max = 1000) String endpoint,
        @NotNull @Valid Keys keys
) {
    public record Keys(@NotBlank @Size(max = 200) String p256dh, @NotBlank @Size(max = 100) String auth) {
    }
}
