-- A barber can delete a service. It disappears everywhere, but the row stays (deleted, inactive)
-- because appointments already booked with it point to it and keep their own copy of its details.
ALTER TABLE service_offerings ADD COLUMN deleted BOOLEAN NOT NULL DEFAULT FALSE;
