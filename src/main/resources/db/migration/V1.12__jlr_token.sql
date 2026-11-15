-- The refresh token of the JLR Data Hub login. It rotates on every use, so the new one must be stored before the old
-- one is forgotten. A single row (id = 1), plain text like the rest of the secrets the app keeps on disk. The access
-- token lives 5 minutes and stays in memory.
create table jlr_token
(
    id            INTEGER not null primary key,
    refresh_token TEXT    not null,
    updated_time  TEXT    not null,
    constraint chk_jlr_token_single_row check (id = 1)
);
