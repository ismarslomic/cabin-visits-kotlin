## Initial load (at startup)

The initial load is executed on every application startup, regardless of whether it has already run before. This ensures
the database is always updated with the latest content from the vehicle trips file. With this approach, you can
change the file and restart the application to apply updates.

```mermaid
sequenceDiagram
    autonumber
    participant InitialLoad as Application: initial load
    participant VehicleTripService as Vehicle Trip Service
    participant CheckInOutSensorService as Check In Out Sensor Service
    participant CheckInOutService as Check In Out Service

    InitialLoad->>VehicleTripService: insert vehicle trips from file
    InitialLoad->>CheckInOutSensorService: fetch check in/out
    InitialLoad->>CheckInOutService: update check in/out status for all reservations
```

## Background sync (continuously)

The background sync task automatically keeps application data in sync with external sources. It launches scheduled tasks
as background coroutines that:

- Periodically synchronize vehicle trips and check-in/out sensor data, but only within both daytime and active
  reservation windows, to minimize unnecessary operations.
- Continuously update the check-in/out status for all reservations based on the latest data.

Each sync task operates at its own configurable interval, and all background tasks are automatically stopped when the
application is shut down to ensure a clean exit.

```mermaid
sequenceDiagram
    autonumber
    participant BackgroundSync as Application: background sync
    participant VehicleTripService as Vehicle Trip Service
    participant CheckInOutSensorService as Check In Out Sensor Service
    participant CheckInOutService as Check In Out Service

    alt is day time
        alt is within reservation window
            loop every 60 minutes (VEHICLE_TRIP_SYNC_FREQ_MINUTES)
                BackgroundSync->>VehicleTripService: fetch vehicle trips
                BackgroundSync->>CheckInOutSensorService: fetch check in/out
                BackgroundSync->>CheckInOutService: update check in/out status for all reservations
            end
        end
    end
```

### Rules

#### Is day time?

`DAYTIME_START (default to 08:00) <= now <= DAYTIME_END (default to 23:00)`

#### Is within reservation window?

`reservation.startTime - 1 day <= now <= reservation.endTime + 1 day`

## Vehicle Trip Service

The `VehicleTripService` manages the synchronization and storage of vehicle trip data within the application. It
provides
two main functions:

1. `insertVehicleTripsFromFile`:
   This function reads vehicle trip data from a specified JSON file, then updates the local database by adding new
   trips, updating changed trips, or skipping unchanged ones.
2. `fetchVehicleTrips`:
   This function connects to an external vehicle trip service. After authenticating, it fetches trip data (using either
   a full or incremental sync based on a saved checkpoint), processes all available pages, and updates the local
   database. It then saves the latest sync date for future use and logs statistics about added, updated, or unchanged
   trips.

Through these methods, VehicleTripService ensures that local records remain consistent with both offline files and
external sources, enabling reliable and up-to-date vehicle trip management.

### insert vehicle trips from file

```mermaid
sequenceDiagram
    autonumber
    participant Application
    participant VehicleTripService as Vehicle Trip Service
    participant tripsFile as file:vehicleTrips.json
    participant VehicleTripRepository as Vehicle Trip Repository
    participant vehicleTrip as db:vehicle_trip

    Application->>VehicleTripService: insert vehicle trips from file
    activate VehicleTripService
    VehicleTripService->>tripsFile: read vehicle trips from json file
    tripsFile-->>VehicleTripService: vehicle trips
    VehicleTripService->>VehicleTripRepository: add/update vehicle trips
    VehicleTripRepository->>vehicleTrip: add/update vehicle trips
    VehicleTripRepository-->>VehicleTripService: persistence result
    deactivate VehicleTripService
```

### fetch vehicle trips

```mermaid
sequenceDiagram
    autonumber
    participant Application
    participant VehicleTripService as Vehicle Trip Service
    participant SyncCheckpointService as Sync Checkpoint Service
    participant SyncCheckpointRepository as Sync Checkpoint Repository
    participant sync_checkpoint as db:sync_checkpoint
    participant VehicleTrips as Http Vehicle Trips (External)
    participant VehicleTripRepository as Vehicle Trip Repository
    participant vehicleTrip as db:vehicle_trip
    
    Application->>VehicleTripService: fetch vehicle trips
    activate VehicleTripService
    
    VehicleTripService->>SyncCheckpointService: checkpoint for Vehicle Trips
    SyncCheckpointService->>SyncCheckpointRepository: checkpoint by id
    SyncCheckpointRepository->>sync_checkpoint: checkpoint by id
    sync_checkpoint-->>SyncCheckpointRepository: checkpoint
    SyncCheckpointRepository-->>SyncCheckpointService: checkpoint
    SyncCheckpointService-->>VehicleTripService: checkpoint
    
    alt is checkpoint null
        VehicleTripService->>VehicleTrips: read all vehicle trips (full sync)
        VehicleTrips-->>VehicleTripService: all vehicle trips
    else
        VehicleTripService->>VehicleTrips: read new vehicle trips since checkpoint (incremental sync)
        VehicleTrips-->>VehicleTripService: new vehicle trips
    end
    
    VehicleTripService->>VehicleTripRepository: add/update vehicle trips
    VehicleTripRepository->>vehicleTrip: add/update vehicle trips
    VehicleTripRepository-->>VehicleTripService: persistence result
    
    VehicleTripService->>SyncCheckpointService: add/update checkpoint for vehicle trips
    SyncCheckpointService->>SyncCheckpointRepository: add/update checkpoint
    SyncCheckpointRepository->>sync_checkpoint: add/update checkpoint
    SyncCheckpointRepository-->>SyncCheckpointService: persistence result
    deactivate VehicleTripService
```

## Check In/Out Sensor Service

The `CheckInOutSensorService` manages the retrieval and storage of check-in and check-out sensor data, primarily for
tracking occupancy and usage over time. It connects to an InfluxDB time-series database, running queries within specific
time ranges to fetch sensor state changes (such as "checked in" or "checked out").
The service supports both full and incremental synchronization based on a checkpoint system, ensuring only new or
unprocessed sensor records are fetched and persisted. After fetching the data, it adds or updates records in the
internal database and updates the checkpoint to the newest processed event, maintaining efficient and accurate
synchronization with the external sensor data source.

```mermaid
sequenceDiagram
    autonumber
    participant Application
    participant CheckInOutSensorService as Check In/Out Sensor Service
    participant SyncCheckpointService as Sync Checkpoint Service
    participant SyncCheckpointRepository as Sync Checkpoint Repository
    participant sync_checkpoint as db:sync_checkpoint
    participant InfluxDb as Influx DB (external)
    participant CheckInOutSensorRepository as Check In/Out Sensor Repository
    participant check_in_out_sensor as db:check_in_out_sensor
    
    Application->>CheckInOutSensorService: fetch check on/out
    activate CheckInOutSensorService
    
    CheckInOutSensorService->>SyncCheckpointService: checkpoint for check in/out sensor
    SyncCheckpointService->>SyncCheckpointRepository: checkpoint by id
    SyncCheckpointRepository->>sync_checkpoint: checkpoint by id
    sync_checkpoint-->>SyncCheckpointRepository: checkpoint
    SyncCheckpointRepository-->>SyncCheckpointService: checkpoint
    SyncCheckpointService-->>CheckInOutSensorService: checkpoint
    
    alt is checkpoint null
        CheckInOutSensorService->>InfluxDb: read all check in/out sensor data (full sync)
        InfluxDb-->>CheckInOutSensorService: all check in/out sensor data
    else
        CheckInOutSensorService->>InfluxDb: read new check in/out sensor data (incremental sync)
        InfluxDb-->>CheckInOutSensorService: new check in/out sensor data
    end
    
    CheckInOutSensorService->>CheckInOutSensorRepository: add/update check in/out sensor data
    CheckInOutSensorRepository->>check_in_out_sensor: add/update check in/out sensor data
    CheckInOutSensorRepository-->>CheckInOutSensorService: persistence result
    
    CheckInOutSensorService->>SyncCheckpointService: add/update checkpoint for InfluxDB check in/out sensor data
    SyncCheckpointService->>SyncCheckpointRepository: add/update checkpoint
    SyncCheckpointRepository->>sync_checkpoint: add/update checkpoint
    SyncCheckpointRepository-->>SyncCheckpointService: persistence result
    
    deactivate CheckInOutSensorService
```

## Check In/Out Service

The `CheckInOutService` orchestrates the detection and recording of guests’ check-in and check-out times for cabin
reservations. It integrates data from reservations, check in/out sensor data, and vehicle trips to determine the most
accurate check-in and check-out events for each reservation.

The function `updateCheckInOutStatusForAllReservations` updates the check-in and check-out status for all reservations
by:

- Fetching all existing reservations, check in/out sensor data (by date), and relevant vehicle trips.
- For each reservation, attempt to determine check-in and check-out times through the following data sources (in
  priority order):
    1. vehicle trip
    2. check in/out sensor data, and
    3. reservation calendar times
- Persisting any new or updated check-in/out details with the reservation record.

By systematically synthesizing information from multiple sources, the service aims to ensure reservation records have
the most accurate and up-to-date check-in and check-out statuses possible.

```mermaid
sequenceDiagram
    autonumber
    participant Application
    participant CheckInOutService as Check In/Out Service
    participant ReservationRepository as Reservation Repository
    participant CheckInOutSensorRepository as Check In/Out Sensor Repository
    participant VehicleTripRepository as Vehicle Trip Repository
    participant CabinVehicleTrip as Cabin Vehicle Trip
    
    Application->>CheckInOutService: update check in/out status for all reservations
    activate CheckInOutService
    
    CheckInOutService->>ReservationRepository: all reservations
    ReservationRepository-->>CheckInOutService: reservations
    
    CheckInOutService->>CheckInOutSensorRepository: all check in/outs
    CheckInOutSensorRepository-->>CheckInOutService: all check in/outs
    
    CheckInOutService->>VehicleTripRepository: all vehicle trips
    VehicleTripRepository-->>CheckInOutService: all vehicle trips
    
    CheckInOutService->>CabinVehicleTrip: find cabin trips with extra stops
    CabinVehicleTrip-->>CheckInOutService: vehicle trips
    
    loop every reservation
        CheckInOutService->>CheckInOutService: create check in
        CheckInOutService->>ReservationRepository: set check in
        ReservationRepository-->>CheckInOutService: persistence result
        
        CheckInOutService->>CheckInOutService: create check out
        CheckInOutService->>ReservationRepository: set check out
        ReservationRepository-->>CheckInOutService: persistence result
    end
    
    deactivate CheckInOutService
```

### Rules

#### Check-In Time Determination Logic

When automatically determining the check-in time for a reservation, the system follows a priority order based on
available data sources:

1. **Vehicle Trip Data**:
   If there is a vehicle trip record that likely represents the guest's arrival at the cabin, and the trip's end time is
   available, this time is used as the check-in time. The source is recorded as "Vehicle Trip".
2. **Check-In Sensor Data**:
   If no suitable vehicle trip is found, but there is a check-in sensor event matching the reservation
   and providing a check-in timestamp, this is used as the check-in time. The source is recorded as "Check-In Sensor".
3. **Reservation Start Time**:
   If neither a relevant vehicle trip nor a sensor event is available, the check-in time defaults to the reservation's
   scheduled start time. The source is indicated as "Calendar Event".

This order ensures that the system uses the **most precise and reliable check-in information available**, falling back
on the calendar when primary sources are missing.

#### Check-Out Time Determination Logic

When automatically determining the check-out time for a reservation, the system follows a prioritized set of rules to
choose the most accurate and relevant timestamp:

1. **Vehicle Trip Data**:
   If there is a vehicle trip that likely corresponds to the guest's departure from the cabin, and the trip's start time
   is available, this time is used as the check-out time. The source is labeled as "Vehicle Trip".
2. **Check-Out Sensor Data**:
   If no suitable vehicle trip is found, but a check-out sensor event provides a check-out time for the
   reservation, this timestamp is selected as the check-out time. The source is labeled as "Check-Our Sensor".
3. **Reservation End Time**:
   If neither a relevant vehicle trip nor a matching sensor event is available, the system defaults to using the
   reservation's scheduled end time as the check-out time. The source is indicated as "Calendar Event".

This logic ensures that the system always records the check-out time using the **best available evidence**, giving
priority to real-world events and falling back on the original reservation details when necessary.
