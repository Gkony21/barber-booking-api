-- A customer asking to move a confirmed appointment to a barber who approves by hand: the new time
-- waits as its own pending row that replaces the confirmed one, which keeps its time until the
-- barber accepts. If the barber declines or does not answer, the confirmed appointment stays.
ALTER TABLE appointments ADD COLUMN replaces_id BIGINT REFERENCES appointments (id) ON DELETE CASCADE;

-- At most one open change per appointment.
CREATE UNIQUE INDEX uq_appointments_replaces ON appointments (replaces_id);

-- An appointment and its own change may overlap (e.g. "half an hour later"); anything else still may not.
ALTER TABLE appointments DROP CONSTRAINT no_overlapping_appointments;
ALTER TABLE appointments ADD CONSTRAINT no_overlapping_appointments EXCLUDE USING gist (
    barber_id WITH =,
    tstzrange(start_at, end_at) WITH &&,
    (coalesce(replaces_id, id)) WITH <>
) WHERE (status IN ('PENDING', 'CONFIRMED'));
