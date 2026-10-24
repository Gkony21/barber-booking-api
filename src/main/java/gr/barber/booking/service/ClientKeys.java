package gr.barber.booking.service;

import gr.barber.booking.model.Appointment;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Which customer an appointment belongs to, in a barber's own list. The mobile number decides, so
 * someone who once phoned and once booked in the app is one customer; without a number (a walk-in),
 * the app account or else the name.
 */
public final class ClientKeys {

    private ClientKeys() {
    }

    public static String of(Appointment appointment) {
        String phone = digits(appointment.getCustomerPhone());
        if (phone != null) {
            return "p" + phone;
        }
        if (appointment.getCustomer() != null) {
            return "u" + appointment.getCustomer().getId();
        }
        return "n" + plain(appointment.getCustomerName());
    }

    // The last ten digits, so "+30 691 234 5678" and "6912345678" are the same number.
    static String digits(String phone) {
        if (phone == null) {
            return null;
        }
        String digits = phone.replaceAll("\\D", "");
        if (digits.length() < 6) {
            return null;
        }
        return digits.length() > 10 ? digits.substring(digits.length() - 10) : digits;
    }

    /** Lower case without accents or extra spaces: "Κώστας  Π." and "κωστας π." match. */
    public static String plain(String text) {
        String folded = Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return folded.toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", " ");
    }
}
