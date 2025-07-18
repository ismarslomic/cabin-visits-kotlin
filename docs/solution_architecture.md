## Initial load (at startup)

```mermaid
sequenceDiagram
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
