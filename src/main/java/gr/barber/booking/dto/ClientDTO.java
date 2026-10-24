package gr.barber.booking.dto;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * One of the barber's customers, as their appointments show them.
 *
 * @param key           stable id in the barber's list (see ClientKeys); used in URLs
 * @param appCustomer   has booked through the app at least once
 * @param visits        appointments that took place
 * @param noShows       times they did not turn up
 * @param lastServiceId with lastExtraIds: what they had last time, to book the same again
 * @param note          the barber's own note; customers never see it
 */
public record ClientDTO(
        String key,
        String name,
        String phone,
        boolean appCustomer,
        int visits,
        int noShows,
        OffsetDateTime lastVisit,
        OffsetDateTime nextVisit,
        Long lastServiceId,
        List<Long> lastExtraIds,
        String note
) {
}
