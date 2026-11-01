-- An email identifies a user (Cloudflare Access), so it can only belong to one guest. Case-insensitive, like the lookup.
-- Guests without an email (NULL) are not affected.
create unique index uq_guest_email on guest (lower(email));
