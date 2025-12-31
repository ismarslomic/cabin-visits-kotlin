package no.slomic.smarthytte.schema.reservations.stats

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import no.slomic.smarthytte.schema.reservations.stats.ReservationStatsUtils.guestStatsComparator

class ReservationStatsUtilsTest :
    ShouldSpec({
        context("guestStatsComparator") {
            val guest1 = GuestVisitStats("1", "A", "Z", 30, 5, 10)
            val guest2 = GuestVisitStats("2", "B", "Y", 30, 5, 15) // More stay days
            val guest3 = GuestVisitStats("3", "C", "X", 30, 10, 10) // More visits, same stay days as guest 1
            val guest4 =
                GuestVisitStats("4", "A", "X", 30, 5, 10) // Same visits/stay days as guest 1, but earlier last name
            val guest5 = GuestVisitStats("5", "B", "X", 30, 5, 10) // Same everything as guest 4, but later first name

            should("sort by totalStayDays descending") {
                val list = listOf(guest1, guest2)
                list.sortedWith(guestStatsComparator()) shouldBe listOf(guest2, guest1)
            }

            should("sort by totalVisits descending when stay days are equal") {
                val list = listOf(guest1, guest3)
                list.sortedWith(guestStatsComparator()) shouldBe listOf(guest3, guest1)
            }

            should("sort by lastName ascending when stay days and visits are equal") {
                val list = listOf(guest1, guest4)
                list.sortedWith(guestStatsComparator()) shouldBe listOf(guest4, guest1)
            }

            should("sort by firstName ascending when stay days, visits, and last name are equal") {
                val list = listOf(guest4, guest5)
                list.sortedWith(guestStatsComparator()) shouldBe listOf(guest4, guest5)
            }

            should("correctly sort a complex list") {
                val list = listOf(guest1, guest2, guest3, guest4, guest5)
                val expectedOrder = listOf(
                    guest2, // 15 days
                    guest3, // 10 days, 10 visits
                    guest4, // 10 days, 5 visits, lastName "X", firstName "A"
                    guest5, // 10 days, 5 visits, lastName "X", firstName "B"
                    guest1, // 10 days, 5 visits, lastName "Z"
                )
                list.sortedWith(guestStatsComparator()) shouldBe expectedOrder
            }
        }
    })
