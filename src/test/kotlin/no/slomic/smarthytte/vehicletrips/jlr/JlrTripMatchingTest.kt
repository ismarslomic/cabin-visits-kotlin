package no.slomic.smarthytte.vehicletrips.jlr

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import no.slomic.smarthytte.vehicletrips.VehicleTrip
import no.slomic.smarthytte.vehicletrips.createTrip
import kotlin.time.Instant

private val END = Instant.parse("2026-11-14T10:30:00Z")

private fun journey(distance: Double = 150.5) = JlrJourneyResponse(
    journeyId = "j1",
    startTime = Instant.parse("2026-11-14T08:00:00Z"),
    endTime = END,
    duration = 9000.0,
    distance = JlrValueUnitResponse(distance, "km"),
    averageSpeed = JlrValueUnitResponse(60.0, "km/h"),
    startLocation = JlrLocationResponse(59.1, 10.1),
    endLocation = JlrLocationResponse(60.2, 8.2),
)

/** An archive trip whose id is [idOffsetMillis] after the end of the journey. */
private fun archiveTrip(
    idOffsetMillis: Long = 1_500,
    distance: Double = 150.5,
    jlrJourneyId: String? = null,
    id: String = (END.toEpochMilliseconds() + idOffsetMillis).toString(),
): VehicleTrip = createTrip(
    startCity = "Oslo",
    endCity = "Ullsåk",
    startTime = "2026-11-14T08:00:00Z",
    endTime = "2026-11-14T10:30:00Z",
    id = id,
    jlrJourneyId = jlrJourneyId,
).copy(distance = distance)

class JlrTripMatchingTest :
    ShouldSpec({
        should("match an archive trip whose id is 1.5 seconds after the end and whose distance is the same") {
            val trip = archiveTrip()

            journey().findMatchIn(listOf(trip)) shouldBe trip
        }

        should("match when the id is up to 10 seconds before or after the end") {
            journey().findMatchIn(listOf(archiveTrip(idOffsetMillis = 10_000))).shouldNotBeNullTrip()
            journey().findMatchIn(listOf(archiveTrip(idOffsetMillis = -10_000))).shouldNotBeNullTrip()
        }

        should("not match when the id is more than 10 seconds from the end") {
            journey().findMatchIn(listOf(archiveTrip(idOffsetMillis = 10_001))).shouldBeNull()
            journey().findMatchIn(listOf(archiveTrip(idOffsetMillis = -10_001))).shouldBeNull()
        }

        should("match when the distance differs by 0.1 km or less, not more") {
            journey().findMatchIn(listOf(archiveTrip(distance = 150.6))).shouldNotBeNullTrip()
            journey().findMatchIn(listOf(archiveTrip(distance = 150.4))).shouldNotBeNullTrip()
            journey().findMatchIn(listOf(archiveTrip(distance = 150.61))).shouldBeNull()
        }

        should("not match a trip that is linked to a journey already") {
            journey().findMatchIn(listOf(archiveTrip(jlrJourneyId = "other"))).shouldBeNull()
        }

        should("not match a trip whose id is not a number") {
            journey().findMatchIn(listOf(archiveTrip(id = "not-a-number"))).shouldBeNull()
        }

        should("choose the trip with the nearest id") {
            val far = archiveTrip(idOffsetMillis = 8_000)
            val near = archiveTrip(idOffsetMillis = 1_000)

            journey().findMatchIn(listOf(far, near)) shouldBe near
        }

        should("give null without candidates") {
            journey().findMatchIn(emptyList()).shouldBeNull()
        }
    })

private fun VehicleTrip?.shouldNotBeNullTrip() {
    (this != null) shouldBe true
}
