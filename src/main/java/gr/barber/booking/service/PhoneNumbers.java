package gr.barber.booking.service;

import gr.barber.booking.exception.BadRequestException;

/** Mobile numbers as people type them ("69 1234 5678", "+30 691-234-5678"), kept without spaces or dashes. */
public final class PhoneNumbers {

    private PhoneNumbers() {
    }

    public static String normalize(String raw) {
        String phone = raw == null ? "" : raw.replaceAll("[\\s\\-().]", "");
        if (phone.isEmpty()) {
            throw new BadRequestException("PHONE_REQUIRED", "A mobile number is required");
        }
        if (!phone.matches("\\+?\\d{10,15}")) {
            throw new BadRequestException("INVALID_PHONE", "Enter a valid mobile number");
        }
        return phone;
    }
}
