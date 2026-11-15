package no.slomic.smarthytte.properties

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import no.slomic.smarthytte.utils.withTestEnvironment

/** The variables without a default, which the configuration needs to load at all. */
private val requiredVariables = mapOf(
    "GOOGLE_CREDENTIALS_FILE_PATH" to "/data/credentials.json",
    "GOOGLE_CALENDAR_ID" to "calendar",
    "INFLUXDB_URL" to "http://localhost:8086",
    "INFLUXDB_TOKEN" to "token",
    "INFLUXDB_ORG" to "org",
    "INFLUXDB_BUCKET" to "bucket",
    "INFLUXDB_CHECK_IN_MEASUREMENT" to "measurement",
    "INFLUXDB_CHECK_IN_RANGE_START" to "2019-01-01T00:00:00Z",
    "VEHICLE_TRIP_FILE_PATH" to "/data/vehicle-trips.json",
    "VEHICLE_TRIP_LOGIN_URL" to "https://mycar.com/login",
    "VEHICLE_TRIP_TRIPS_URL" to "https://mycar.com/trips",
    "VEHICLE_TRIP_USERNAME" to "name",
    "VEHICLE_TRIP_PASSWORD" to "password",
    "VEHICLE_TRIP_SYNC_FROM_DATE" to "2020-01-01",
    "VEHICLE_TRIP_USER_AGENT" to "agent",
    "VEHICLE_TRIP_REFERRER" to "https://myapp.com",
    "VEHICLE_TRIP_LOCALE" to "key=nb_NO",
    "STATISTICS_DATA_START_DATE" to "2020-09-01",
    "HOME_LATITUDE" to "59.1",
    "HOME_LONGITUDE" to "10.1",
    "HOME_ADDRESS" to "Homestreet 1, 0100 Oslo",
    "CABIN_LATITUDE" to "60.2",
    "CABIN_LONGITUDE" to "8.2",
    "CABIN_ADDRESS" to "Cabinroad 2, 3560 Hemsedal",
)

class BootstrapAdminPropertiesTest :
    StringSpec({
        "bootstrap admin properties should be blank when no variable is set" {
            withTestEnvironment(requiredVariables) {
                loadProperties<BootstrapAdminPropertiesHolder>().bootstrapAdmin shouldBe
                    BootstrapAdminProperties(email = "", firstName = "", lastName = "", birthYear = "", gender = "")
            }
        }

        "bootstrap admin properties should be read from the environment variables" {
            val variables = mapOf(
                "BOOTSTRAP_ADMIN_EMAIL" to "ola@example.com",
                "BOOTSTRAP_ADMIN_FIRST_NAME" to "Ola",
                "BOOTSTRAP_ADMIN_LAST_NAME" to "Nordmann",
                "BOOTSTRAP_ADMIN_BIRTH_YEAR" to "1980",
                "BOOTSTRAP_ADMIN_GENDER" to "MALE",
            )

            withTestEnvironment(requiredVariables + variables) {
                loadProperties<BootstrapAdminPropertiesHolder>().bootstrapAdmin shouldBe
                    BootstrapAdminProperties("ola@example.com", "Ola", "Nordmann", "1980", "MALE")
            }
        }
    })
