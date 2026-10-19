-- Requests nobody answered are closed automatically 2 hours before they start (EXPIRED), so the
-- customer hears back and the time is freed. The barber is reminded once while one waits.
ALTER TABLE appointments DROP CONSTRAINT appointments_status_check;
ALTER TABLE appointments ADD CONSTRAINT appointments_status_check
    CHECK (status IN ('PENDING', 'CONFIRMED', 'REJECTED', 'CANCELLED', 'EXPIRED'));
ALTER TABLE appointments ADD COLUMN request_reminder_sent_at TIMESTAMPTZ;

-- How long before an appointment customers must book it: 30 minutes, 1 hour or 2 hours.
ALTER TABLE barbers ADD COLUMN min_notice_minutes INT NOT NULL DEFAULT 60
    CHECK (min_notice_minutes IN (30, 60, 120));
