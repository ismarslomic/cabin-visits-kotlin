package no.slomic.smarthytte.utils

/**
 * The variables without a default in `application.yml`. The whole file is resolved, so loading any property needs
 * them.
 */
val REQUIRED_APP_ENV = mapOf(
    "GOOGLE_CREDENTIALS_FILE_PATH" to "/data/key.json",
    "GOOGLE_CALENDAR_ID" to "calendar",
    "INFLUXDB_URL" to "http://localhost:8086",
    "INFLUXDB_TOKEN" to "token",
    "INFLUXDB_ORG" to "org",
    "INFLUXDB_BUCKET" to "bucket",
    "INFLUXDB_CHECK_IN_MEASUREMENT" to "measurement",
    "INFLUXDB_CHECK_IN_RANGE_START" to "2019-01-01T00:00:00Z",
    "VEHICLE_TRIP_FILE_PATH" to "/data/trips.json",
    "VEHICLE_TRIP_SYNC_FROM_DATE" to "2020-01-01",
    "JLR_CLIENT_ID" to "client-1",
    "JLR_VIN" to "SAL0000000VIN0001",
    "JLR_PRIVATE_KEY" to "-----BEGIN PRIVATE KEY-----\\nabc\\n-----END PRIVATE KEY-----",
    "STATISTICS_DATA_START_DATE" to "2020-09-01",
    "HOME_LATITUDE" to "59.1",
    "HOME_LONGITUDE" to "10.1",
    "HOME_ADDRESS" to "Homestreet 1, 0100 Oslo",
    "CABIN_LATITUDE" to "60.2",
    "CABIN_LONGITUDE" to "8.2",
    "CABIN_ADDRESS" to "Cabinroad 2, 3560 Hemsedal",
)
