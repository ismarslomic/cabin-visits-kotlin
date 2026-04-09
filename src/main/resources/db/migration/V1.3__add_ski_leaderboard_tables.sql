create table ski_stats_profile
(
    created_time      TEXT               not null,
    updated_time      TEXT,
    version           SMALLINT default 1 not null,
    id                VARCHAR(50)        not null
        constraint pk_ski_stats_profile_id primary key,
    name              VARCHAR(100)       not null,
    profile_image_url VARCHAR(500),
    is_private        BOOLEAN            not null,
    constraint chk_ski_stats_profile_signed_short_version check (version BETWEEN -32768 AND 32767)
);

create table ski_stats_leaderboard_entry
(
    created_time               TEXT               not null,
    updated_time               TEXT,
    version                    SMALLINT default 1 not null,
    id                         VARCHAR(100)       not null
        constraint pk_ski_stats_leaderboard_entry_id primary key,
    profile_id                 VARCHAR(50)        not null,
    period_type                VARCHAR(10)        not null,
    period_value               VARCHAR(20)        not null,
    start_date                 TEXT               not null,
    week_id                    VARCHAR(10),
    week_number                INT,
    season_id                  VARCHAR(10)        not null,
    season_name                VARCHAR(20)        not null default '',
    year                       INT,
    leaderboard_updated_at_utc TEXT               not null,
    entry_user_id              VARCHAR(50)        not null
        references ski_stats_profile (id) on delete cascade,
    position                   INT                not null,
    drop_height_in_meter       INT                not null,
    constraint chk_ski_stats_leaderboard_entry_signed_short_version check (version BETWEEN -32768 AND 32767)
);
