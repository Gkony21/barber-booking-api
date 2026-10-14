-- When each appointment reminder email was sent (null = not yet). Set atomically so a reminder
-- goes out once even if two app instances run the job at the same time.
ALTER TABLE appointments ADD COLUMN reminder_day_sent_at TIMESTAMPTZ;
ALTER TABLE appointments ADD COLUMN reminder_soon_sent_at TIMESTAMPTZ;

-- When the person accepted the terms and privacy policy at sign-up (null for older accounts).
ALTER TABLE users ADD COLUMN terms_accepted_at TIMESTAMPTZ;
