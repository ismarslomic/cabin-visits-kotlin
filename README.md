# cabin-visits-kotlin

Kotlin app using Ktor server application with GraalVM to create HTTP endpoints to receive data from different sources to
collect all data belonging to a visit at Slomic Smarthytte and store it to a database.

## Licensing Information

This project uses Oracle GraalVM Native Image, which is subject to
the [Oracle GraalVM Free Terms and Conditions (GFTC)](https://www.oracle.com/downloads/licenses/graal-free-license.html).

## Run in Docker

The changes to this app is automatically published to the Docker Hub and you can always find the latest release at
[ismarslomic/cabin-visits-kotlin](https://hub.docker.com/r/ismarslomic/cabin-visits-kotlin)

Set the following environment variables, either by using the `.env` file and the `--env-file` option or by setting them
with the `-e` option:

| Variable                                     | Required | Default                | Description                                                                                                                                                                                                       |
|----------------------------------------------|----------|------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `GOOGLE_CREDENTIALS_FILE_PATH `              | Yes      | N/A                    | The path to the Google Service Account credentials internally in the docker container                                                                                                                             |
| `GOOGLE_CALENDAR_ID`                         | Yes      | N/A                    | The id of the Google Calendar which you want to synchronize continuously                                                                                                                                          |
| `GOOGLE_CALENDAR_SYNC_FROM_DATE_TIME`        | No       | `2024-01-01T00:00:00Z` | Lower bound (exclusive) for an event's end time to filter by when doing full calendar sync. Must be an RFC3339 timestamp with mandatory time zone offset.                                                         |
| `GOOGLE_CALENDAR_SUMMARY_TO_GUEST_FILE_PATH` | Yes      | N/A                    | The path to the JSON file defining the Calendar event summary to Guest Id mapping. Path is internally in the docker container. See example [summaryToGuestIds.json](./src/test/resources/summaryToGuestIds.json). |
| `GUEST_FILE_PATH`                            | Yes      | N/A                    | The path to the JSON file containing the Guests to be inserted/updated in the database. Path is internally in the docker container. See example [guests.json](./src/test/resources/guests.json).                  |

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
