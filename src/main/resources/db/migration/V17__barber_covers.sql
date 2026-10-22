-- A barber's own cover image for the top of their page and their card in the list. Before, the
-- newest work photo was used; now work photos are only the gallery, and a barber without a cover
-- gets the app's default design.
ALTER TABLE barbers ADD COLUMN cover_key VARCHAR(64);
