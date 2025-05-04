create table check_in_out_sensor
(
    created_time TEXT               not null,
    updated_time TEXT,
    version      SMALLINT default 1 not null,
    id           VARCHAR(30)        not null
        constraint pk_check_in_out_sensor_id primary key,
    time         TEXT               not null,
    status       VARCHAR(15)        not null,
    constraint chk_check_in_out_sensor_signed_short_version check (version BETWEEN -32768 AND 32767)
);

create table google_calendar_sync
(
    id           INTEGER
        primary key autoincrement,
    sync_token   VARCHAR(100) not null,
    updated_time TEXT         not null,
    constraint chk_google_calendar_sync_signed_integer_id check (id BETWEEN -2147483648 AND 2147483647)
);

create table sync_checkpoint
(
    created_time     TEXT               not null,
    updated_time     TEXT,
    version          SMALLINT default 1 not null,
    id               VARCHAR(50)        not null
        constraint pk_sync_checkpoint_id primary key,
    checkpoint_value VARCHAR(100)       not null,
    constraint chk_sync_checkpoint_signed_short_version check (version BETWEEN -32768 AND 32767)
);

create table guest
(
    created_time TEXT               not null,
    updated_time TEXT,
    version      SMALLINT default 1 not null,
    id           VARCHAR(50)        not null
        constraint pk_guest_id primary key,
    first_name   VARCHAR(20)        not null,
    last_name    VARCHAR(20)        not null,
    birth_year   SMALLINT           not null,
    email        VARCHAR(255),
    gender       VARCHAR(10)        not null,
    notion_id    VARCHAR(50),
    constraint chk_guest_signed_short_birth_year check (birth_year BETWEEN -32768 AND 32767),
    constraint chk_guest_signed_short_version check (version BETWEEN -32768 AND 32767)
);

create table reservation
(
    created_time          TEXT               not null,
    updated_time          TEXT,
    version               SMALLINT default 1 not null,
    id                    VARCHAR(1024)      not null
        constraint pk_reservation_id primary key,
    summary               VARCHAR(1000),
    description           VARCHAR(2000),
    start_time            TEXT               not null,
    end_time              TEXT               not null,
    source_created_time   TEXT,
    source_updated_time   TEXT,
    notion_id             VARCHAR(50),
    check_in_time         TEXT,
    check_in_source_name  VARCHAR(20),
    check_in_source_id    VARCHAR(1024),
    check_out_time        TEXT,
    check_out_source_name VARCHAR(20),
    check_out_source_id   VARCHAR(1024),
    constraint chk_reservation_signed_short_version check (version BETWEEN -32768 AND 32767)
);

create table reservation_guest
(
    reservation_id VARCHAR(1024) not null
        constraint fk_reservation_guest_reservation_id__id references reservation on update restrict on delete cascade,
    guest_id       VARCHAR(50)   not null
        constraint fk_reservation_guest_guest_id__id references guest on update restrict on delete cascade,
    constraint pk_reservation_guest primary key (reservation_id, guest_id)
);

create table vehicle_trip
(
    created_time                TEXT               not null,
    updated_time                TEXT,
    version                     SMALLINT default 1 not null,
    id                          VARCHAR(50)        not null
        constraint pk_vehicle_trip_id primary key,
    avg_energy_consumption      DOUBLE PRECISION   not null,
    avg_energy_consumption_unit VARCHAR(10)        not null,
    avg_speed                   DOUBLE PRECISION   not null,
    distance                    DOUBLE PRECISION   not null,
    distance_unit               VARCHAR(5)         not null,
    duration                    BIGINT             not null,
    duration_unit               VARCHAR(5)         not null,
    end_address                 VARCHAR(100)       not null,
    end_city                    VARCHAR(30)        not null,
    end_time                    TEXT               not null,
    energy_regenerated          DOUBLE PRECISION   not null,
    energy_generated_unit       VARCHAR(5)         not null,
    speed_unit                  VARCHAR(5)         not null,
    start_address               VARCHAR(100)       not null,
    start_city                  VARCHAR(30)        not null,
    start_time                  TEXT               not null,
    total_distance              DOUBLE PRECISION   not null,
    notion_id                   VARCHAR(50),
    constraint chk_vehicle_trip_signed_short_version check (version BETWEEN -32768 AND 32767)
);
