-- How often a barber's appointments may start: every 15 or 30 minutes. 30 avoids small unusable
-- gaps for half-hour services; existing barbers get it too.
ALTER TABLE barbers ADD COLUMN slot_step_minutes INTEGER NOT NULL DEFAULT 30
    CHECK (slot_step_minutes IN (15, 30));

-- Optional short description of a work photo, e.g. "Skin fade #0 με σχέδιο".
ALTER TABLE barber_photos ADD COLUMN caption VARCHAR(80);
