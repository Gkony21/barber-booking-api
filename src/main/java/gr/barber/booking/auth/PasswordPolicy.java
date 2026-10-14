package gr.barber.booking.auth;

import gr.barber.booking.exception.WeakPasswordException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Follows current guidance (NIST SP 800-63B): length matters, composition rules ("one capital,
 * one symbol") do not, but passwords that are common, trivially patterned or equal to the
 * account's email are refused. Length (8–100) is checked on the request itself.
 */
@Component
public class PasswordPolicy {

    private final Set<String> common;

    public PasswordPolicy() {
        try {
            String list = new ClassPathResource("security/common-passwords.txt").getContentAsString(StandardCharsets.UTF_8);
            this.common = list.lines()
                    .map(String::strip)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .map(line -> line.toLowerCase(Locale.ROOT))
                    .collect(Collectors.toUnmodifiableSet());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void check(String password, String email) {
        String value = password.toLowerCase(Locale.ROOT);

        if (common.contains(value)) {
            throw new WeakPasswordException("This password is too common");
        }
        if (value.chars().distinct().count() <= 2) {
            throw new WeakPasswordException("This password is too easy to guess");
        }
        if (isSequence(value)) {
            throw new WeakPasswordException("This password is too easy to guess");
        }
        if (email != null) {
            String address = email.toLowerCase(Locale.ROOT);
            String localPart = address.contains("@") ? address.substring(0, address.indexOf('@')) : address;
            if (value.equals(address) || value.equals(localPart)) {
                throw new WeakPasswordException("The password cannot be your email");
            }
        }
    }

    // "abcdefgh", "23456789", "98765432"
    private static boolean isSequence(String value) {
        int step = value.charAt(1) - value.charAt(0);
        if (Math.abs(step) != 1) {
            return false;
        }
        for (int i = 2; i < value.length(); i++) {
            if (value.charAt(i) - value.charAt(i - 1) != step) {
                return false;
            }
        }
        return true;
    }
}
