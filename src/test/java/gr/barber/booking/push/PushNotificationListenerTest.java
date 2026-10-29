package gr.barber.booking.push;

import gr.barber.booking.notification.AppointmentEvent;
import gr.barber.booking.notification.AppointmentEventType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PushNotificationListenerTest {

    private static final ZoneId ATHENS = ZoneId.of("Europe/Athens");

    private static AppointmentEvent event(AppointmentEventType type, ZonedDateTime original) {
        return new AppointmentEvent(type, "maria@test.gr", "Μαρία", "6900000000", "nikos@test.gr", "Nikos", "Κούρεμα",
                List.of(), new BigDecimal("12.00"), ZonedDateTime.of(2030, 1, 8, 18, 0, 0, 0, ATHENS), false, original);
    }

    @Test
    void saysWhatHappenedWithTheNameOnlyAsALabel() {
        assertThat(PushNotificationListener.messageFor(event(AppointmentEventType.CANCELLED_BY_CUSTOMER, null)))
                .isEqualTo(new PushMessage("Ακύρωση ραντεβού", "Τρίτη 8 Ιανουαρίου, 18:00 · Κούρεμα · Μαρία", "/barber"));
        assertThat(PushNotificationListener.messageFor(event(AppointmentEventType.CHANGE_REQUESTED,
                ZonedDateTime.of(2030, 1, 8, 11, 0, 0, 0, ATHENS))))
                .isEqualTo(new PushMessage("Αίτημα αλλαγής ώρας", "Τρίτη 8 Ιανουαρίου, 11:00 → Τρίτη 8 Ιανουαρίου, 18:00 · Μαρία", "/barber"));
    }

    @Test
    void staysQuietAboutWhatTheBarberDidThemselves() {
        assertThat(PushNotificationListener.messageFor(event(AppointmentEventType.CANCELLED_BY_BARBER, null))).isNull();
        assertThat(PushNotificationListener.messageFor(event(AppointmentEventType.REMINDER_DAY, null))).isNull();
    }
}
