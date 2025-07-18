## Initial load (at startup)

```mermaid
sequenceDiagram
    autonumber
    participant InitialLoad as Initial load
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
    participant BackgroundSync as Background Sync
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
    participant GoogleCalendarService as Google Calendar Service
    participant SyncCheckpointService as Sync Checkpoint Service
    participant sync_checkpoint as db:sync_checkpoint
    participant Calendar as Calendar (Google)
    participant ReservationRepository as Reservation Repository
    
    GoogleCalendarService->>SyncCheckpointService: checkpoint for Google Calendar events
    SyncCheckpointService->>sync_checkpoint: checkpointById
    sync_checkpoint-->>SyncCheckpointService: checkpoint
    SyncCheckpointService-->>GoogleCalendarService: checkpoint
    
    alt is checkpoint null
        GoogleCalendarService->>Calendar: read all events (full sync)
        Calendar-->>GoogleCalendarService: all events
    else
        GoogleCalendarService->>Calendar: read new/updated events since checkpoint (incremental sync)
        Calendar-->>GoogleCalendarService: new/updated events
    end
    
    GoogleCalendarService->>ReservationRepository: add, update or delete reservations
    ReservationRepository-->>GoogleCalendarService: persistence result
    
    GoogleCalendarService->>SyncCheckpointService: add/update checkpoint for Google Calendar events
```

## Guest Service

## Vehicle Trip Service

## Check In/Out Sensor Service

## Check In/Out Service
