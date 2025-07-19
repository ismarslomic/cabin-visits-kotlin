## Initial load (at startup)

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
