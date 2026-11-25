-- Keep the shipper's operating address in the MySQL profile.
-- Run once against an existing food_delivery database before deploying the API update.
ALTER TABLE users ADD COLUMN address_line VARCHAR(500) NULL AFTER phone;
