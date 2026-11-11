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

## Calendar outbox (reservation changes to Google Calendar)

Google Calendar is a one-way notification: the app owns the reservations and never reads the calendar back. A reservation
write (create, change of the times, delete) adds a row to `calendar_outbox` in the same transaction; a background task
pushes the rows to Google. A reservation write never waits for Google, and a Google outage never fails a write.

### Data flow

```mermaid
flowchart LR
    user["Admin / family member<br/>(Cloudflare Access)"] -->|"POST / PUT / DELETE<br/>/api/reservations"| api["Reservation routes<br/>+ ReservationService"]

    subgraph tx["One SQLite write transaction"]
        direction TB
        res[("reservation")]
        outbox[("calendar_outbox<br/>operation INSERT / UPDATE / DELETE<br/>attempts, last_error, next_attempt_time")]
    end
    api -->|"write + enqueue"| tx
    api -.->|"times changed: clear check-in/out and trips,<br/>derived again by the check-in/out job"| res

    subgraph bg["Background task, started with the sync jobs"]
        drain["CalendarOutboxDrain<br/>one run at a time, every 30 s<br/>max 50 due rows per run"]
        pusher["CalendarEventPusher<br/>decides insert / patch / restore / delete"]
        gateway["GoogleCalendarGateway<br/>client created on first use"]
        drain --> pusher --> gateway
    end
    outbox -->|"due rows, oldest first,<br/>grouped per reservation"| drain
    drain -->|"success: delete the pushed rows only<br/>failure: attempts + 1, last_error, backoff"| outbox

    gateway -->|"event id = reservation id<br/>timed, Europe/Oslo, no notifications"| google["Google Calendar"]
    google -->|"status + reason"| gateway

    drain -->|"calendar_outbox_pending<br/>calendar_outbox_oldest_age_seconds"| prom["/metrics (Prometheus)"]
    drain -->|"stuck rows, config errors: ERROR log"| logs["Application log"]
```

### One push, step by step

```mermaid
sequenceDiagram
    participant DB as SQLite (calendar_outbox)
    participant Drain as CalendarOutboxDrain
    participant Pusher as CalendarEventPusher
    participant Google as Google Calendar
    loop every drain interval (30 s)
        Drain->>DB: read due rows (next_attempt_time <= now), max batch size
        Note over Drain: group per reservation, the last row decides:<br/>DELETE means delete, anything else means upsert with the current times
        Drain->>Pusher: upsert(reservation) or delete(reservation)
        alt first operation was INSERT
            Pusher->>Google: insert (id = reservation id)
            opt 409, id already exists (e.g. deleted earlier)
                Pusher->>Google: patch with status confirmed (restore)
            end
        else UPDATE
            Pusher->>Google: patch start + end (date cleared, all-day becomes timed)
            opt 404 / 410, event is gone
                Pusher->>Google: patch with status confirmed (restore)
                opt still 404 / 410
                    Pusher->>Google: insert
                end
            end
        else DELETE
            Pusher->>Google: delete (404 / 410 counts as success)
        end
        Google-->>Pusher: result
        alt pushed (or terminal and dropped)
            Drain->>DB: delete the pushed row ids only (a change written meanwhile keeps its own row)
        else failed
            Drain->>DB: attempts + 1, last_error, next_attempt_time = now + backoff
        end
    end
```

### What happens on failure

| Result from Google | Action | Wait before next try |
|--------------------|--------|----------------------|
| Network error, timeout, 5xx, 429, 403 rate limit | Retry, WARN log | `start x factor^(attempts-1)`, at most the cap (default 30 s, x2, 1 h) |
| 403 `quotaExceeded` | Keep the row, ERROR log | The cap |
| 401, other 403, other 4xx (credentials, sharing, bad request) | Keep the row, ERROR log | Backoff |
| 400 on insert, 409 after a failed restore | Terminal: the row is dropped | None |
| Legacy reservation id (not `a-v0-9`, 5+ chars, e.g. starts with `_`) | Never inserted, ERROR log, the row is dropped | None |

When the oldest row has waited longer than the stuck threshold (default 60 min), the drain logs an ERROR on every run.
See "Google Calendar notifications (outbox)" in `DEVELOPER.md` for the settings (`CALENDAR_*`) and operations.

### Metrics

The drain publishes the state of the outbox as two Prometheus gauges on `GET /metrics` (unauthenticated, the Pi is only
reachable through Cloudflare). The drain sets the values after every run, so a scrape never reads the database.

| Metric | Meaning | Healthy value | Act when |
|--------|---------|---------------|----------|
| `calendar_outbox_pending` | Rows in `calendar_outbox`, including rows that wait for a retry | 0, or a few for up to one drain interval after a write | It stays above 0 and grows, or never returns to 0 |
| `calendar_outbox_oldest_age_seconds` | Age of the oldest row, 0 when the outbox is empty | 0, or below the drain interval (30 s) | It passes the stuck threshold (`CALENDAR_OUTBOX_STUCK_THRESHOLD_MINUTES`, default 60 min); the drain also logs an ERROR |

Reading them: a growing `pending` with a small `oldest_age` means the drain works and writes come faster than pushes (not a
problem). A growing `oldest_age` means one or more rows fail, so check `last_error` in `calendar_outbox` and the ERROR logs.
Both values freeze if the drain loop stops, so alert on the metric being absent or constant for a long time as well. The
gauges exist from startup and report 0 until the first drain run.

Example alert rules (Prometheus):

```yaml
- alert: CalendarOutboxStuck
  expr: calendar_outbox_oldest_age_seconds > 3600
  for: 5m
- alert: CalendarOutboxNotDraining
  expr: calendar_outbox_pending > 0 and changes(calendar_outbox_pending[1h]) == 0 and calendar_outbox_oldest_age_seconds > 600
```

The application also exposes the standard JVM, process and Ktor request metrics on the same endpoint.

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
    participant ReservationSystemService as Reservation System Service
    participant CheckInOutSensorQueryService as Check In/Out Sensor Query Service
    participant VehicleTripQueryService as Vehicle Trip Query Service
    participant CabinVehicleTrip as Cabin Vehicle Trip
    
    Application->>CheckInOutService: update check in/out status for all reservations
    activate CheckInOutService
    
    CheckInOutService->>ReservationSystemService: all reservations
    ReservationSystemService-->>CheckInOutService: reservations
    
    CheckInOutService->>CheckInOutSensorQueryService: all check in/outs
    CheckInOutSensorQueryService-->>CheckInOutService: all check in/outs
    
    CheckInOutService->>VehicleTripQueryService: all vehicle trips
    VehicleTripQueryService-->>CheckInOutService: all vehicle trips
    
    CheckInOutService->>CabinVehicleTrip: find cabin trips with extra stops
    CabinVehicleTrip-->>CheckInOutService: vehicle trips
    
    loop every reservation
        CheckInOutService->>CheckInOutService: create check in
        CheckInOutService->>ReservationSystemService: set check in
        ReservationSystemService-->>CheckInOutService: persistence result
        
        CheckInOutService->>CheckInOutService: create check out
        CheckInOutService->>ReservationSystemService: set check out
        ReservationSystemService-->>CheckInOutService: persistence result
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
