-- Time off can now be part of a day (a doctor's appointment 12:00-14:00): a single date with a
-- start and end time. Without times it stays whole days, as before.
ALTER TABLE time_off ADD COLUMN start_time TIME;
ALTER TABLE time_off ADD COLUMN end_time TIME;
ALTER TABLE time_off ADD CONSTRAINT time_off_block_times CHECK (
    (start_time IS NULL AND end_time IS NULL)
    OR (start_time IS NOT NULL AND end_time IS NOT NULL AND start_time < end_time AND start_date = end_date));

-- The barber can mark a confirmed appointment the customer did not turn up for.
ALTER TABLE appointments DROP CONSTRAINT appointments_status_check;
ALTER TABLE appointments ADD CONSTRAINT appointments_status_check
    CHECK (status IN ('PENDING', 'CONFIRMED', 'REJECTED', 'CANCELLED', 'EXPIRED', 'NO_SHOW'));

-- Minutes kept free after each appointment (cleaning, a breather): 0, 5, 10 or 15.
ALTER TABLE barbers ADD COLUMN buffer_minutes INT NOT NULL DEFAULT 0 CHECK (buffer_minutes IN (0, 5, 10, 15));
