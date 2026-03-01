create table ski_stats_token
(
    created_time             TEXT               not null,
    updated_time             TEXT,
    version                  SMALLINT default 1 not null,
    id                       VARCHAR(50)        not null
        constraint pk_ski_stats_token_id primary key,
    access_token             TEXT               not null,
    refresh_token            TEXT               not null,
    expires_at_epoch_seconds BIGINT,
    constraint chk_ski_stats_token_signed_short_version check (version BETWEEN -32768 AND 32767)
);
