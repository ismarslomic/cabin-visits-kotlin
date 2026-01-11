create table reservation_vehicle_trip
(
    reservation_id  VARCHAR(1024) NOT NULL,
    vehicle_trip_id VARCHAR(50)   NOT NULL,
    trip_type       VARCHAR(20)   NOT NULL, -- TO_CABIN, AT_CABIN, FROM_CABIN
    constraint pk_reservation_vehicle_trip primary key (reservation_id, vehicle_trip_id),
    constraint fk_rvt_reservation_id foreign key (reservation_id) references reservation (id) on delete cascade,
    constraint fk_rvt_vehicle_trip_id foreign key (vehicle_trip_id) references vehicle_trip (id) on delete cascade
);
