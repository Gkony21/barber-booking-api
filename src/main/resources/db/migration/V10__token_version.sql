-- Sign-in tokens carry this number; raising it (e.g. on a password reset) makes every token
-- issued before stop working, on all devices.
ALTER TABLE users ADD COLUMN token_version INT NOT NULL DEFAULT 0;
