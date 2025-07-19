## Initial load (at startup)

The initial load is executed on every application startup, regardless of whether it has already run before. This ensures
the database is always updated with the latest content from static files (such as guests, vehicle trips, and
summary-to-guest ID mappings). With this approach, you can change the static files and restart the application to
apply updates.

```mermaid
sequenceDiagram
    autonumber
    participant InitialLoad as Application: initial load
    participant GuestService as Guest Service
    participant VehicleTripService as Vehicle Trip Service
    participant CheckInOutSensorService as Check In Out Sensor Service
    participant CheckInOutService as Check In Out Service
    participant GoogleCalendarService as Google Calendar Service

    InitialLoad->>GuestService: insert guests from file
    InitialLoad->>VehicleTripService: insert vehicle trips from file
    InitialLoad->>CheckInOutSensorService: fetch check in/out
    InitialLoad->>GoogleCalendarService: fetch google calendar events
    InitialLoad->>GoogleCalendarService: fetch google calendar events
    InitialLoad->>CheckInOutService: update check in/out status for all reservations
```

## Background sync (continuously)

The background sync task automatically keeps application data in sync with external sources. It launches scheduled tasks
as background coroutines that:

- Regularly fetch and update reservations from Google Calendar during designated daytime hours.
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
    participant GoogleCalendarService as Google Calendar Service

    alt is day time
        loop every 10 minutes (GOOGLE_CALENDAR_SYNC_FREQ_MINUTES)
            BackgroundSync->>GoogleCalendarService: fetch google calendar events
        end
        
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

## Google Calendar Service

The Google Calendar Service automates the synchronization of booking reservations by periodically retrieving events from
a designated Google Calendar. It supports both full and incremental synchronization by leveraging sync tokens, ensuring
that only new or changed events are fetched after the initial sync.

Key responsibilities include:

- Fetching events from Google Calendar using either a complete pull or updates based on the last sync state.
- Storing new events as reservations, updating existing ones if changes are detected, and removing reservations if
  events are deleted or canceled in the calendar.
- Handling paginated event data and ensuring changes are persistently stored.
- Maintaining sync checkpoints to enable efficient incremental updates and resilience in the face of errors such as
  invalid sync tokens.

This service ensures that the application's reservation database always reflects the current state of the linked Google
Calendar, providing a robust and up-to-date booking management workflow.

```mermaid
sequenceDiagram
    autonumber
    participant Application
    participant GoogleCalendarService as Google Calendar Service
    participant SyncCheckpointService as Sync Checkpoint Service
    participant SyncCheckpointRepository as SyncCheckpointRepository
    participant sync_checkpoint as db:sync_checkpoint
    participant Calendar as Calendar (Google)
    participant ReservationRepository as Reservation Repository
    participant reservation as db:reservation
   
    Application->>GoogleCalendarService: fetch google calendar events
    activate GoogleCalendarService
    GoogleCalendarService->>SyncCheckpointService: checkpoint for Google Calendar events
    SyncCheckpointService->>SyncCheckpointRepository: checkpoint by id
    SyncCheckpointRepository->>sync_checkpoint: checkpoint by id
    sync_checkpoint-->>SyncCheckpointRepository: checkpoint
    SyncCheckpointRepository-->>SyncCheckpointService: checkpoint
    SyncCheckpointService-->>GoogleCalendarService: checkpoint
    
    alt is checkpoint null
        GoogleCalendarService->>Calendar: read all events (full sync)
        Calendar-->>GoogleCalendarService: all events
    else
        GoogleCalendarService->>Calendar: read new/updated events since checkpoint (incremental sync)
        Calendar-->>GoogleCalendarService: new/updated events
    end
    
    GoogleCalendarService->>ReservationRepository: add, update or delete reservations
    ReservationRepository->>reservation: add, update or delete reservations
    ReservationRepository-->>GoogleCalendarService: persistence result
    
    GoogleCalendarService->>SyncCheckpointService: add/update checkpoint for Google Calendar events
    SyncCheckpointService->>SyncCheckpointRepository: add/update checkpoint
    SyncCheckpointRepository->>sync_checkpoint: add/update checkpoint
    SyncCheckpointRepository-->>SyncCheckpointService: persistence result
    deactivate GoogleCalendarService
```

## Guest Service

The GuestService is responsible for managing guest information within the application. It reads guest data from a
designated JSON file, then processes and updates the internal guest database accordingly. As it processes the guests, it
ensures that new guests are added, existing guests are updated if their details have changed, and unchanged records are
left as-is.

```mermaid
sequenceDiagram
    autonumber
    participant Application
    participant GuestService as Guest Service
    participant guestFile as file:guests.json
    participant GuestRepository as Guest Repository
    participant guest as db:guest

    Application->>GuestService: insert guests from file
    activate GuestService
    GuestService->>guestFile: read guests from json file
    guestFile-->>GuestService: guests
    GuestService->>GuestRepository: add/update guests
    GuestRepository->>guest: add/update guests
    GuestRepository-->>GuestService: persistence result
    deactivate GuestService
```

## Vehicle Trip Service

The VehicleTripService manages the synchronization and storage of vehicle trip data within the application. It provides
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
    participant SyncCheckpointRepository as SyncCheckpointRepository
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

## Check In/Out Service
