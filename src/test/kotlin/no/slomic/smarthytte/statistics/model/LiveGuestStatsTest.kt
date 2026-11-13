package no.slomic.smarthytte.statistics.model

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate
import no.slomic.smarthytte.reservations.GuestStay
import no.slomic.smarthytte.statistics.calculator.liveGuestFixture

/** LiveGuestStats repeats the properties of GuestStats (a flat type, so a client that reads them does not break). */
class LiveGuestStatsTest :
    ShouldSpec({
        fun names(descriptor: kotlinx.serialization.descriptors.SerialDescriptor) =
            (0 until descriptor.elementsCount).map { descriptor.getElementName(it) }

        should("have the properties of GuestStats, in the same order, plus the two stay dates") {
            names(LiveGuestStats.serializer().descriptor) shouldBe
                names(GuestStats.serializer().descriptor) + listOf("stayStartDate", "stayEndDate")
        }

        should("convert a GuestStats with the dates of the stay, and back without them") {
            val stats = liveGuestFixture("anna", isFirstVisit = true, allTimeVisits = 4)
            val stay = GuestStay("anna", "r1", LocalDate(2026, 7, 11), LocalDate(2026, 7, 12))

            val live = stats.toLive(stay)

            live.stayStartDate shouldBe LocalDate(2026, 7, 11)
            live.stayEndDate shouldBe LocalDate(2026, 7, 12)
            live.toGuestStats() shouldBe stats
        }
    })
