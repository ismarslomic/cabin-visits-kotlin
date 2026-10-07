# cabin-visits-kotlin

[![Code coverage](https://codecov.io/gh/ismarslomic/cabin-visits-kotlin/branch/main/graph/badge.svg)](https://codecov.io/gh/ismarslomic/cabin-visits-kotlin)

Kotlin app using Ktor server application with GraalVM to create HTTP endpoints to receive data from different sources to
collect all data belonging to a visit at Slomic Smarthytte and store it to a database.

## Licensing Information

This project uses Oracle GraalVM Native Image, which is subject to
the [Oracle GraalVM Free Terms and Conditions (GFTC)](https://www.oracle.com/downloads/licenses/graal-free-license.html).

## Run in Docker

The changes to this app are automatically published to Docker Hub, and you can always find the latest release at
[ismarslomic/cabin-visits-kotlin](https://hub.docker.com/r/ismarslomic/cabin-visits-kotlin)

Set the following environment variables, either by using the `.env` file and the `--env-file` option or by setting them
with the `-e` option:

| Variable                                     | Required | Default                | Description                                                                                                                                                                                                                                |
|----------------------------------------------|----------|------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `GOOGLE_CREDENTIALS_FILE_PATH`               | Yes      | N/A                    | The path to the Google Service Account credentials, inside the container.                                                                                                                                                                  |
| `GOOGLE_CALENDAR_ID`                         | Yes      | N/A                    | The id of the Google Calendar to synchronize.                                                                                                                                                                                              |
| `GOOGLE_CALENDAR_SYNC_ENABLED`               | No       | `true`                 | Enable or disable polling of Google Calendar. When `false`, no sync is performed (also not the initial load).                                                                                                                              |
| `GOOGLE_CALENDAR_SYNC_FROM_DATE_TIME`        | No       | `2024-01-01T00:00:00Z` | Lower bound for event's end time in full calendar sync (RFC3339, with time zone).                                                                                                                                                          |
| `GOOGLE_CALENDAR_SUMMARY_TO_GUEST_FILE_PATH` | Yes      | N/A                    | The path to the JSON file defining the Calendar event summary-to-Guest mapping, inside the container.                                                                                                                                      |
| `GUEST_FILE_PATH`                            | Yes      | N/A                    | Path to the JSON file with the Guests for database update/insert.                                                                                                                                                                          |
| `GUEST_AVATARS_DIRECTORY_PATH`               | Yes      | N/A                    | Path to the directory with the Guests avatars images (named in format guestId.jpg).                                                                                                                                                        |
| `STATISTICS_DATA_START_DATE`                 | Yes      | N/A                    | First date visits are registered (format: `YYYY-MM-DD`), used in fun facts about the cabin.                                                                                                                                                |
| `GOOGLE_CALENDAR_SYNC_FREQ_MINUTES`          | No       | 10                     | Frequency (in minutes) to poll Google Calendar for updates.                                                                                                                                                                                |
| `INFLUXDB_URL`                               | Yes      | N/A                    | Url to the InfluxDb database (e.g., `http://192.0.0.1:8086`).                                                                                                                                                                              |
| `INFLUXDB_TOKEN`                             | Yes      | N/A                    | Access Token for InfluxDb authentication.                                                                                                                                                                                                  |
| `INFLUXDB_ORG`                               | Yes      | N/A                    | InfluxDb organization.                                                                                                                                                                                                                     |
| `INFLUXDB_BUCKET`                            | Yes      | N/A                    | InfluxDb bucket name.                                                                                                                                                                                                                      |
| `INFLUXDB_CHECK_IN_MEASUREMENT`              | Yes      | N/A                    | InfluxDb measurement for check-in sensor.                                                                                                                                                                                                  |
| `INFLUXDB_CHECK_IN_RANGE_START`              | Yes      | N/A                    | Earliest date for check-in sync (RFC3339 datetime, with time zone).                                                                                                                                                                        |
| `INFLUXDB_CHECK_IN_RANGE_STOP`               | No       | now                    | Latest date for check-in sync (RFC3339 datetime, or `now`).                                                                                                                                                                                |
| `INFLUXDB_CHECK_IN_SYNC_ENABLED`             | No       | `true`                 | Enable or disable check-in sync from InfluxDb. When `false`, no sync is performed (also not the initial load).                                                                                                                             |
| `INFLUXDB_CHECK_IN_SYNC_FREQ_MINUTES`        | No       | 10                     | Frequency (in minutes) for check-in and vehicle trip sync (they share the same background task).                                                                                                                                           |
| `VEHICLE_TRIP_FILE_PATH`                     | Yes      | N/A                    | The path to the JSON file defining vehicle trips, inside the container.                                                                                                                                                                    |
| `VEHICLE_TRIP_LOGIN_URL`                     | Yes      | N/A                    | API endpoint URL for performing login before fetching the vehicle trip data.                                                                                                                                                               |
| `VEHICLE_TRIP_TRIPS_URL`                     | Yes      | N/A                    | API endpoint URL for fetching the vehicle trip data.                                                                                                                                                                                       |
| `VEHICLE_TRIP_USERNAME`                      | Yes      | N/A                    | Username for authenticating against the vehicle trip API.                                                                                                                                                                                  |
| `VEHICLE_TRIP_PASSWORD`                      | Yes      | N/A                    | Password for authenticating against the vehicle trip API.                                                                                                                                                                                  |
| `VEHICLE_TRIP_SYNC_ENABLED`                  | No       | `true`                 | Enable or disable syncing of vehicle trips from the API. When `false`, no sync is performed.                                                                                                                                               |
| `VEHICLE_TRIP_SYNC_FROM_DATE`                | Yes      | N/A                    | Lower bound for vehicle trip start date in full vehicle trip sync (format: `YYYY-MM-DD`).                                                                                                                                                  |
| `VEHICLE_TRIP_USER_AGENT`                    | Yes      | N/A                    | User-Agent HTTP header when making requests to vehicle trip API request.                                                                                                                                                                   |
| `VEHICLE_TRIP_REFERRER`                      | Yes      | N/A                    | Referrer HTTP header for vehicle trip API requests.                                                                                                                                                                                        |
| `VEHICLE_TRIP_LOCALE`                        | Yes      | N/A                    | Locale header for requests (e.g., `nb_NO`, dictates language/formatting).                                                                                                                                                                  |
| `VEHICLE_TRIP_PAGE_SIZE`                     | No       | 200                    | Determines the number of vehicle trip records to fetch per page when syncing vehicle trip data from the API. Defaults to `200`. Adjusting this value can help manage paging performance and memory usage when dealing with large datasets. |

Run the app with Docker:

```bash
docker run --rm -p 8079:8079 --env-file .env ismarslomic/cabin-visits-kotlin:1.0.0
```

Or use Docker Compose:

Note: make sure the folder /data and the sqlite db file on the host is writable

```yaml
services:
  cabin-visits:
    container_name: cabin-visits
    image: ismarslomic/cabin-visits-kotlin:1.0.0
    ports:
      - 8079:8079 # port for REST API
    volumes:
      - ./config:/config # the google credentials file
      - ./data:/data # sqlite db file, guests.json file, `avatars/` folder with guest pictures, vehicle trips and the summary-to-guest mapping file
    env_file:
      - .env
    restart: unless-stopped
```

```bash
docker compose up  -d
```

## HTTP API

```bash
curl http://localhost:8079
```

# Cabin Usage Metrics

All statistics only include visits that have **started** (arrival date is today or earlier, in UTC). Future bookings
are excluded, while an ongoing visit is included. The only exception is the next upcoming visit shown by the live
endpoint (`/api/stats`).

## Visits

**Definition**  
The number of distinct cabin visits. A visit starts at the arrival time and ends at the departure time.

**Source**  
Each stay is counted **once**, based on its **arrival date**.

**Example**

- 2026-03-27 18:00 → 2026-04-06 15:00

Result:

- March: **1 visit**
- April: **0 visits**

---

## Days

**Definition**  
The number of **calendar days** during which the cabin was occupied **within a specified period** (e.g. a month,
quarter, or year). Both the arrival day and departure day are included.

**Source**  
Count all calendar dates within the selected period that overlap the stay.

**Example**

Stay:

- 2026-03-27 18:00 → 2026-04-06 15:00

Result:

- March: **5 days** (27–31)
- April: **6 days** (1–6)
- Total: **11 days**

---

## Nights

**Definition**  
The number of nights spent at the cabin **within a specified period** (e.g. a month, quarter, or year).

**Source**  
Count all nights within the selected period. A night belongs to the calendar date on which it begins.

**Example**

Stay:

- 2026-03-27 18:00 → 2026-04-06 15:00

Result:

- March: **5 nights**
- April: **5 nights**
- Total: **10 nights**

---

## Occupancy

**Definition**  
The percentage of calendar days that the cabin was occupied during a given period.

**Source**  
Calculated from **Days**.

Formula:

```
Occupancy = Days / Total calendar days in period × 100
```

**Example**

Year:

- Days: **94**
- Total days: **365**

Result:

```
Occupancy = 94 / 365 × 100 = 25.8%
```

---

## Stay

**Definition**  
The duration of a single cabin visit, measured in **calendar days**. A stay always represents the **full duration of a
visit** and is **not split across reporting periods**. For period-based statistics, a stay is attributed to the period
in which the **visit started** (arrival date).

**Source**  
Calculated from the total number of **Days** for a single visit. The complete stay is always associated with the arrival
period. The same rule applies when a stay is measured in **Nights**.

**Example**

Stay:

- 2026-03-27 18:00 → 2026-04-06 15:00

Result:

- Duration: **11-day stay** (**10 nights**)
- Arrival month: **March**

Examples of period-based statistics:

- March:
    - Longest stay: **11 days** (**10 nights**)
    - Average stay: **11 days** (**10 nights**)
- April:
    - Longest stay: **—**
    - Average stay: **—**

---

## Guests

**Definition**  
Visits and days per guest within a specified period (e.g. a month or year).

**Source**  
Guest **visits** follow the **Visits** definition: each stay is counted once per guest, based on its **arrival date**.
Guest **days** follow the **Days** definition: all calendar dates within the selected period that overlap the guest's
stays.

**Example**

Stay with guest A:

- 2026-03-27 18:00 → 2026-04-06 15:00

Result:

- March: guest A has **1 visit** and **5 days**
- April: guest A has **0 visits** and **6 days**

## Metrics

```bash
curl http://localhost:8079/metrics
```
