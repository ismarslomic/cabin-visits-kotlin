-- vehicle_trip is rebuilt because SQLite cannot drop NOT NULL. Trips from the JLR Data Hub have no energy values, and
-- no address or city when the place lookup fails. New columns: data_quality (COMPLETE for every old row),
-- jlr_journey_id (unique) and the coordinates, so places can be recomputed later.

-- The foreign key of reservation_vehicle_trip has on delete cascade: dropping vehicle_trip would delete the links, so
-- keep a copy and put them back
create table reservation_vehicle_trip_backup as
select reservation_id, vehicle_trip_id, trip_type
from reservation_vehicle_trip;

create table vehicle_trip_new
(
    created_time                TEXT               not null,
    updated_time                TEXT,
    version                     SMALLINT default 1 not null,
    id                          VARCHAR(50)        not null
        constraint pk_vehicle_trip_id primary key,
    avg_energy_consumption      DOUBLE PRECISION,
    avg_energy_consumption_unit VARCHAR(10),
    avg_speed                   DOUBLE PRECISION   not null,
    distance                    DOUBLE PRECISION   not null,
    distance_unit               VARCHAR(5)         not null,
    duration                    BIGINT             not null,
    duration_unit               VARCHAR(5)         not null,
    end_address                 VARCHAR(100),
    end_city                    VARCHAR(30),
    end_time                    TEXT               not null,
    energy_regenerated          DOUBLE PRECISION,
    energy_generated_unit       VARCHAR(5),
    speed_unit                  VARCHAR(5)         not null,
    start_address               VARCHAR(100),
    start_city                  VARCHAR(30),
    start_time                  TEXT               not null,
    total_distance              DOUBLE PRECISION   not null,
    notion_id                   VARCHAR(50),
    created_by                  TEXT               not null default 'system',
    updated_by                  TEXT,
    data_quality                TEXT               not null default 'COMPLETE',
    jlr_journey_id              VARCHAR(50),
    start_latitude              DOUBLE PRECISION,
    start_longitude             DOUBLE PRECISION,
    end_latitude                DOUBLE PRECISION,
    end_longitude               DOUBLE PRECISION,
    constraint chk_vehicle_trip_signed_short_version check (version BETWEEN -32768 AND 32767),
    constraint chk_vehicle_trip_data_quality check (data_quality in ('COMPLETE', 'PLACE_MISSING'))
);

insert into vehicle_trip_new (created_time, updated_time, version, id, avg_energy_consumption,
                              avg_energy_consumption_unit, avg_speed, distance, distance_unit, duration, duration_unit,
                              end_address, end_city, end_time, energy_regenerated, energy_generated_unit, speed_unit,
                              start_address, start_city, start_time, total_distance, notion_id, created_by, updated_by)
select created_time, updated_time, version, id, avg_energy_consumption,
       avg_energy_consumption_unit, avg_speed, distance, distance_unit, duration, duration_unit,
       end_address, end_city, end_time, energy_regenerated, energy_generated_unit, speed_unit,
       start_address, start_city, start_time, total_distance, notion_id, created_by, updated_by
from vehicle_trip;

drop table vehicle_trip;
alter table vehicle_trip_new rename to vehicle_trip;

create unique index uq_vehicle_trip_jlr_journey_id on vehicle_trip (jlr_journey_id);
create index idx_vehicle_trip_data_quality on vehicle_trip (data_quality);

delete from reservation_vehicle_trip;
insert into reservation_vehicle_trip (reservation_id, vehicle_trip_id, trip_type)
select reservation_id, vehicle_trip_id, trip_type
from reservation_vehicle_trip_backup;
drop table reservation_vehicle_trip_backup;
