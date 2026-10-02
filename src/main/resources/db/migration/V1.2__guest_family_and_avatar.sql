alter table guest
    add column is_family INTEGER not null default 0;

alter table guest
    add column avatar_image BLOB;
