-- Appointments the barber enters for phone or walk-in customers have no account: they carry the
-- customer's name (and optionally phone) instead. Every appointment has one or the other.
ALTER TABLE appointments ALTER COLUMN customer_id DROP NOT NULL;
ALTER TABLE appointments ADD COLUMN guest_name VARCHAR(100);
ALTER TABLE appointments ADD COLUMN guest_phone VARCHAR(30);
ALTER TABLE appointments ADD CONSTRAINT appointments_customer_or_guest
    CHECK (customer_id IS NOT NULL OR guest_name IS NOT NULL);

-- Short public link per barber, e.g. /giorgos. Filled in for existing barbers on startup.
ALTER TABLE barbers ADD COLUMN slug VARCHAR(40) UNIQUE;
