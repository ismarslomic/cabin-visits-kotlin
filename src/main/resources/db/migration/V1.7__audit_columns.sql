-- Audit columns: created_by (required) and updated_by (set only when an existing row is updated).
-- Existing rows get created_by = 'system'; updated_by = 'system' only where updated_time is set.
-- The default on created_by stays as a safety net, the application always sets the actor explicitly.
alter table reservation add column created_by text not null default 'system';
alter table reservation add column updated_by text;
update reservation set updated_by = 'system' where updated_time is not null;

alter table guest add column created_by text not null default 'system';
alter table guest add column updated_by text;
update guest set updated_by = 'system' where updated_time is not null;

alter table vehicle_trip add column created_by text not null default 'system';
alter table vehicle_trip add column updated_by text;
update vehicle_trip set updated_by = 'system' where updated_time is not null;

-- Same two columns on the two background tables, so all six tables have the same audit columns
alter table check_in_out_sensor add column created_by text not null default 'system';
alter table check_in_out_sensor add column updated_by text;
update check_in_out_sensor set updated_by = 'system' where updated_time is not null;

alter table sync_checkpoint add column created_by text not null default 'system';
alter table sync_checkpoint add column updated_by text;
update sync_checkpoint set updated_by = 'system' where updated_time is not null;

-- guest_avatar is a leaf table: rebuilt because created_time must be not null (copied from updated_time),
-- updated_time must become nullable and it gets a version like the other tables
create table guest_avatar_new
(
    guest_id     VARCHAR(50) not null
        constraint pk_guest_avatar_guest_id primary key
        constraint fk_guest_avatar_guest_id__id references guest on update restrict on delete cascade,
    image        BLOB        not null,
    created_time TEXT        not null,
    created_by   TEXT        not null,
    updated_time TEXT,
    updated_by   TEXT,
    version      SMALLINT default 1 not null,
    constraint chk_guest_avatar_signed_short_version check (version BETWEEN -32768 AND 32767)
);
insert into guest_avatar_new (guest_id, image, created_time, created_by, updated_time, updated_by)
select guest_id, image, updated_time, 'system', updated_time, 'system'
from guest_avatar;
drop table guest_avatar;
alter table guest_avatar_new rename to guest_avatar;
