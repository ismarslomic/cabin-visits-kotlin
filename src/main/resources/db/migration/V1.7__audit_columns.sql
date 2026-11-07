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

-- guest_avatar is a leaf table: rebuilt because created_time must be not null (copied from updated_time) and
-- updated_time must become nullable
create table guest_avatar_new
(
    guest_id     VARCHAR(50) not null
        constraint pk_guest_avatar_guest_id primary key
        constraint fk_guest_avatar_guest_id__id references guest on update restrict on delete cascade,
    image        BLOB        not null,
    created_time TEXT        not null,
    created_by   TEXT        not null,
    updated_time TEXT,
    updated_by   TEXT
);
insert into guest_avatar_new (guest_id, image, created_time, created_by, updated_time, updated_by)
select guest_id, image, updated_time, 'system', updated_time, 'system'
from guest_avatar;
drop table guest_avatar;
alter table guest_avatar_new rename to guest_avatar;
