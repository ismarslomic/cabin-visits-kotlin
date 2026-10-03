alter table guest
    add column is_family INTEGER not null default 0;

create table guest_avatar
(
    guest_id     VARCHAR(50) not null
        constraint pk_guest_avatar_guest_id primary key
        constraint fk_guest_avatar_guest_id__id references guest on update restrict on delete cascade,
    image        BLOB        not null,
    updated_time TEXT        not null
);
