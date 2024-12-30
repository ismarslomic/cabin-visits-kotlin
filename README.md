# cabin-visits-kotlin

Kotlin app using Ktor server application with GraalVM to create HTTP endpoints to receive data from different sources to
collect all data belonging to a visit at Slomic Smarthytte and store it to a database.

## Licensing Information

This project uses Oracle GraalVM Native Image, which is subject to
the [Oracle GraalVM Free Terms and Conditions (GFTC)](https://www.oracle.com/downloads/licenses/graal-free-license.html).

## Run in Docker

The changes to this app is automatically published to the Docker Hub and you can always find the latest release at
[ismarslomic/cabin-visits-kotlin](https://hub.docker.com/r/ismarslomic/cabin-visits-kotlin)

Set following environment variables, either by using the `.env` file and the `--env-file` option or by setting them
with the `-e` option:

- `GOOGLE_CREDENTIALS_FILE_PATH` - the path to the Google Service Account credentials internally in the docker container
- `GOOGLE_CALENDAR_ID` - The id of the Google Calendar which you want to synchronize continuously

```bash
docker run --rm -p 8079:8079 ismarslomic/cabin-visits-kotlin:main
```

## HTTP API

```bash
curl http://localhost:8079
```

## Metrics

```bash
curl http://localhost:8079/metrics
```
