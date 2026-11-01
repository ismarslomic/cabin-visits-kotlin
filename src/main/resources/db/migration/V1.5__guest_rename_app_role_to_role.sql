-- The role is called role everywhere in the application. The check constraint keeps its original name (SQLite cannot
-- rename a constraint), but its condition follows the renamed column.
alter table guest
    rename column app_role to role;

-- The guest fields changed through the API are stored by name, see GuestField.
update guest
set api_edited_fields = replace(api_edited_fields, 'APP_ROLE', 'ROLE')
where api_edited_fields like '%APP_ROLE%';
