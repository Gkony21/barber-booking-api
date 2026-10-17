-- How far ahead customers can book this barber: 14 days, a month or two months.
ALTER TABLE barbers ADD COLUMN booking_window_days INT NOT NULL DEFAULT 14
    CHECK (booking_window_days IN (14, 30, 60));
