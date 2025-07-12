package no.slomic.smarthytte.vehicletrips

import io.kotest.assertions.json.shouldEqualJson
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json

class VehicleTripServiceTest :
    StringSpec({
        val fromDate = LocalDate(dayOfMonth = 1, monthNumber = 2, year = 2025)
        val toDate = LocalDate(dayOfMonth = 5, monthNumber = 2, year = 2025)

        "From and to dates should be formatted to norwegian format in the http request" {
            val request: GetVehicleTripsRequest = createRequest(fromDate, toDate)

            request.fromDateInUserFormat shouldBe "01.02.2025"
            request.toDateInUserFormat shouldBe "05.02.2025"
        }

        "The http request should be serialized to valid JSON and optional properties shall be present" {
            val request: GetVehicleTripsRequest = createRequest(fromDate, toDate)

            val actualJsonRequest: String = Json.encodeToString(request)

            val expectedJsonRequest: String = """
            {
                "fetchJourneys": true,
                "currentPage": 1,
                "pageSize": 20,
                "sorting": ["START_DATE-asc"],
                "fromDateInUserFormat": "01.02.2025",
                "toDateInUserFormat": "05.02.2025",
                "selectedJourneys": []
            }
            """.trimIndent()

            actualJsonRequest shouldEqualJson expectedJsonRequest
        }
    })
