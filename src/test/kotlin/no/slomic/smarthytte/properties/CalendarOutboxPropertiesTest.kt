package no.slomic.smarthytte.properties

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import no.slomic.smarthytte.utils.withTestEnvironment

// The whole application.yml is resolved, so the variables without a default must be set
private val requiredEnv = mapOf(
    "GOOGLE_CREDENTIALS_FILE_PATH" to "/data/key.json",
    "GOOGLE_CALENDAR_ID" to "calendar",
    "INFLUXDB_URL" to "http://localhost:8086",
    "INFLUXDB_TOKEN" to "token",
    "INFLUXDB_ORG" to "org",
    "INFLUXDB_BUCKET" to "bucket",
    "INFLUXDB_CHECK_IN_MEASUREMENT" to "measurement",
    "INFLUXDB_CHECK_IN_RANGE_START" to "2019-01-01T00:00:00Z",
    "VEHICLE_TRIP_FILE_PATH" to "/data/trips.json",
    "VEHICLE_TRIP_LOGIN_URL" to "https://car.example/login",
    "VEHICLE_TRIP_TRIPS_URL" to "https://car.example/trips",
    "VEHICLE_TRIP_USERNAME" to "user",
    "VEHICLE_TRIP_PASSWORD" to "password",
    "VEHICLE_TRIP_SYNC_FROM_DATE" to "2020-01-01",
    "VEHICLE_TRIP_USER_AGENT" to "agent",
    "VEHICLE_TRIP_REFERRER" to "https://app.example",
    "VEHICLE_TRIP_LOCALE" to "key=nb_NO",
    "STATISTICS_DATA_START_DATE" to "2020-09-01",
)

class CalendarOutboxPropertiesTest :
    StringSpec({
        "the calendar outbox properties should have a default for every value" {
            val properties = withTestEnvironment(requiredEnv) {
                loadProperties<CalendarOutboxPropertiesHolder>().calendarOutbox
            }

            properties shouldBe CalendarOutboxProperties(
                eventTitle = "Hyttebesøk",
                batchSize = 50,
                drainIntervalSeconds = 30,
                backoffStartSeconds = 30,
                backoffFactor = 2.0,
                backoffCapSeconds = 3600,
                stuckThresholdMinutes = 60,
            )
        }

        "the calendar outbox properties should be read from the environment variables" {
            val env = mapOf(
                "CALENDAR_EVENT_TITLE" to "Cabin",
                "CALENDAR_OUTBOX_BATCH_SIZE" to "10",
                "CALENDAR_OUTBOX_DRAIN_INTERVAL_SECONDS" to "5",
                "CALENDAR_OUTBOX_BACKOFF_START_SECONDS" to "7",
                "CALENDAR_OUTBOX_BACKOFF_FACTOR" to "1.5",
                "CALENDAR_OUTBOX_BACKOFF_CAP_SECONDS" to "99",
                "CALENDAR_OUTBOX_STUCK_THRESHOLD_MINUTES" to "15",
            )

            withTestEnvironment(requiredEnv + env) {
                loadProperties<CalendarOutboxPropertiesHolder>().calendarOutbox shouldBe CalendarOutboxProperties(
                    "Cabin",
                    10,
                    5,
                    7,
                    1.5,
                    99,
                    15,
                )
            }
        }
    })
