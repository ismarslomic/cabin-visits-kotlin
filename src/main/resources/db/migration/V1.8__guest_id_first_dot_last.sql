-- Guest ids become <firstname>.<lastname> (for example ola.nordmann), the same slug that POST /api/guests generates
-- (GuestSlug.kt): lowercase, Æ/Ø/Å/Ö/Ä/Ü/É transliterated, every run of other characters becomes one dot, at most 50
-- characters. A name that gives the same slug as another guest gets -2, -3, ... (an id that is already correct keeps it).
--
-- guest_id is referenced by reservation_guest and guest_avatar with "on update restrict", and uq_guest_email is unique,
-- so a guest is not updated in place: the new row is inserted first (without email), the references are moved, the old
-- row is deleted, and the email is set on the new row last. version and the audit columns of the guests are copied
-- unchanged: a new id is not a user edit.
--
-- The guest id is also stored as plain text in created_by/updated_by of every table, which is updated at the end.

create temp table guest_id_map
(
    old_id TEXT not null primary key,
    email  TEXT,
    name   TEXT not null,
    new_id TEXT
);

insert into guest_id_map (old_id, email, name)
select id, email, trim(first_name) || ' ' || trim(last_name)
from guest;

-- Same transliteration as GuestSlug.kt. lower() only handles ASCII, so the upper case letters are replaced first.
update guest_id_map
set name = replace(replace(replace(replace(replace(replace(replace(name,
    'Æ', 'ae'), 'Ø', 'o'), 'Å', 'a'), 'Ö', 'o'), 'Ä', 'a'), 'Ü', 'u'), 'É', 'e');
update guest_id_map
set name = lower(name);
update guest_id_map
set name = replace(replace(replace(replace(replace(replace(replace(name,
    'æ', 'ae'), 'ø', 'o'), 'å', 'a'), 'ö', 'o'), 'ä', 'a'), 'ü', 'u'), 'é', 'e');

-- Space, hyphen and apostrophe become a dot, then runs of dots are collapsed (4 rounds handle runs of up to 16)
update guest_id_map
set name = replace(replace(replace(name, ' ', '.'), '-', '.'), '''', '.');
update guest_id_map set name = replace(name, '..', '.');
update guest_id_map set name = replace(name, '..', '.');
update guest_id_map set name = replace(name, '..', '.');
update guest_id_map set name = replace(name, '..', '.');
update guest_id_map
set name = trim(substr(trim(name, '.'), 1, 50), '.');
update guest_id_map
set name = 'guest'
where name = '';

-- A guest whose id is already the slug keeps it and ranks first, so nobody else takes that id
with ranked as (select old_id,
                       row_number() over (partition by name order by old_id <> name, old_id) as number
                from guest_id_map)
update guest_id_map
set new_id = case
                 when ranked.number = 1 then guest_id_map.name
                 else substr(guest_id_map.name, 1, 50 - length('-' || ranked.number)) || '-' || ranked.number
    end
from ranked
where ranked.old_id = guest_id_map.old_id;

delete from guest_id_map where old_id = new_id;

insert into guest (id, first_name, last_name, birth_year, email, gender, notion_id, is_family, role,
                   created_time, created_by, updated_time, updated_by, version)
select m.new_id, g.first_name, g.last_name, g.birth_year, null, g.gender, g.notion_id, g.is_family, g.role,
       g.created_time, g.created_by, g.updated_time, g.updated_by, g.version
from guest g
         join guest_id_map m on m.old_id = g.id;

update reservation_guest
set guest_id = (select new_id from guest_id_map where old_id = reservation_guest.guest_id)
where guest_id in (select old_id from guest_id_map);

update guest_avatar
set guest_id = (select new_id from guest_id_map where old_id = guest_avatar.guest_id)
where guest_id in (select old_id from guest_id_map);

delete from guest where id in (select old_id from guest_id_map);

update guest
set email = (select email from guest_id_map where new_id = guest.id)
where id in (select new_id from guest_id_map);

-- The actor columns hold a guest id (or 'system')
update guest set created_by = (select new_id from guest_id_map where old_id = guest.created_by)
where created_by in (select old_id from guest_id_map);
update guest set updated_by = (select new_id from guest_id_map where old_id = guest.updated_by)
where updated_by in (select old_id from guest_id_map);
update guest_avatar set created_by = (select new_id from guest_id_map where old_id = guest_avatar.created_by)
where created_by in (select old_id from guest_id_map);
update guest_avatar set updated_by = (select new_id from guest_id_map where old_id = guest_avatar.updated_by)
where updated_by in (select old_id from guest_id_map);
update reservation set created_by = (select new_id from guest_id_map where old_id = reservation.created_by)
where created_by in (select old_id from guest_id_map);
update reservation set updated_by = (select new_id from guest_id_map where old_id = reservation.updated_by)
where updated_by in (select old_id from guest_id_map);
update vehicle_trip set created_by = (select new_id from guest_id_map where old_id = vehicle_trip.created_by)
where created_by in (select old_id from guest_id_map);
update vehicle_trip set updated_by = (select new_id from guest_id_map where old_id = vehicle_trip.updated_by)
where updated_by in (select old_id from guest_id_map);
update check_in_out_sensor
set created_by = (select new_id from guest_id_map where old_id = check_in_out_sensor.created_by)
where created_by in (select old_id from guest_id_map);
update check_in_out_sensor
set updated_by = (select new_id from guest_id_map where old_id = check_in_out_sensor.updated_by)
where updated_by in (select old_id from guest_id_map);
update sync_checkpoint set created_by = (select new_id from guest_id_map where old_id = sync_checkpoint.created_by)
where created_by in (select old_id from guest_id_map);
update sync_checkpoint set updated_by = (select new_id from guest_id_map where old_id = sync_checkpoint.updated_by)
where updated_by in (select old_id from guest_id_map);

drop table guest_id_map;
