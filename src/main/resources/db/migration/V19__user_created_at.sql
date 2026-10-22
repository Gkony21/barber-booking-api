-- When each account was made: unverified ones are removed after a while, and sign-ups per day are capped.
ALTER TABLE users ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT now();
CREATE INDEX idx_users_created_at ON users (created_at);
