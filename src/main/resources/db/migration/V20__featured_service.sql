-- The service a barber shows on their card in the list (e.g. "Κούρεμα 12 €"), so the price customers
-- see is the usual one and not the cheapest (a kids' cut). At most one per barber.
ALTER TABLE service_offerings ADD COLUMN featured BOOLEAN NOT NULL DEFAULT FALSE;
CREATE UNIQUE INDEX uq_service_offerings_featured ON service_offerings (barber_id) WHERE featured;
