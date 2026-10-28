alter table guest
    add column app_role VARCHAR(20)
        constraint chk_guest_app_role check (app_role in ('ADMIN', 'FAMILY_USER', 'GUEST_USER'));

-- Comma separated names of the guest fields changed through the API. The guests.json sync leaves these fields alone.
alter table guest
    add column api_edited_fields VARCHAR(255);
