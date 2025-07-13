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

| Variable                                     | Required | Default                | Description                                                                                           |
|----------------------------------------------|----------|------------------------|-------------------------------------------------------------------------------------------------------|
| `GOOGLE_CREDENTIALS_FILE_PATH`               | Yes      | N/A                    | The path to the Google Service Account credentials, inside the container.                             |
| `GOOGLE_CALENDAR_ID`                         | Yes      | N/A                    | The id of the Google Calendar to synchronize.                                                         |
| `GOOGLE_CALENDAR_SYNC_FROM_DATE_TIME`        | No       | `2024-01-01T00:00:00Z` | Lower bound for event's end time in full calendar sync (RFC3339, with time zone).                     |
| `GOOGLE_CALENDAR_SUMMARY_TO_GUEST_FILE_PATH` | Yes      | N/A                    | The path to the JSON file defining the Calendar event summary-to-Guest mapping, inside the container. |
| `GUEST_FILE_PATH`                            | Yes      | N/A                    | Path to the JSON file with the Guests for database update/insert.                                     |
| `GOOGLE_CALENDAR_SYNC_FREQ_MINUTES`          | No       | 10                     | Frequency (in minutes) to poll Google Calendar for updates.                                           |
| `INFLUXDB_URL`                               | Yes      | N/A                    | Url to the InfluxDb database (e.g., `http://192.0.0.1:8086`).                                         |
| `INFLUXDB_TOKEN`                             | Yes      | N/A                    | Access Token for InfluxDb authentication.                                                             |
| `INFLUXDB_ORG`                               | Yes      | N/A                    | InfluxDb organization.                                                                                |
| `INFLUXDB_BUCKET`                            | Yes      | N/A                    | InfluxDb bucket name.                                                                                 |
| `INFLUXDB_CHECK_IN_MEASUREMENT`              | Yes      | N/A                    | InfluxDb measurement for check-in sensor.                                                             |
| `INFLUXDB_CHECK_IN_RANGE_START`              | Yes      | N/A                    | Earliest date for check-in sync (RFC3339 datetime, with time zone).                                   |
| `INFLUXDB_CHECK_IN_RANGE_STOP`               | No       | now                    | Latest date for check-in sync (RFC3339 datetime, or `now`).                                           |
| `INFLUXDB_CHECK_IN_SYNC_FREQ_MINUTES`        | No       | 10                     | Frequency (in minutes) for check-in sync from InfluxDb.                                               |
| `VEHICLE_TRIP_FILE_PATH`                     | Yes      | N/A                    | The path to the JSON file defining vehicle trips, inside the container.                               |
| `VEHICLE_TRIP_LOGIN_URL`                     | Yes      | N/A                    | API endpoint URL for performing login before fetching the vehicle trip data.                          |
| `VEHICLE_TRIP_TRIPS_URL`                     | Yes      | N/A                    | API endpoint URL for fetching the vehicle trip data.                                                  |
| `VEHICLE_TRIP_USERNAME`                      | Yes      | N/A                    | Username for authenticating against the vehicle trip API.                                             |
| `VEHICLE_TRIP_PASSWORD`                      | Yes      | N/A                    | Password for authenticating against the vehicle trip API.                                             |
| `VEHICLE_TRIP_SYNC_FROM_DATE`                | Yes      | N/A                    | Lower bound for vehicle trip start date in full vehicle trip sync (format: `YYYY-MM-DD`).             |
| `VEHICLE_TRIP_SYNC_FREQ_MINUTES`             | No       | 60                     | Frequency (in minutes) for polling and syncing new vehicle trips using the API.                       |
| `VEHICLE_TRIP_USER_AGENT`                    | Yes      | N/A                    | User-Agent HTTP header when making requests to vehicle trip API request.                              |
| `VEHICLE_TRIP_REFERRER`                      | Yes      | N/A                    | Referrer HTTP header for vehicle trip API requests.                                                   |
| `VEHICLE_TRIP_LOCALE`                        | Yes      | N/A                    | Locale header for requests (e.g., `nb_NO`, dictates language/formatting).                             |

```bash
docker run --rm -p 8079:8079 --env-file .env ismarslomic/cabin-visits-kotlin:main
```

## HTTP API

```bash
curl http://localhost:8079
```

## Metrics

```bash
curl http://localhost:8079/metrics
```
