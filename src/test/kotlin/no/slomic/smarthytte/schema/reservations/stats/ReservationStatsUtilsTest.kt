package no.slomic.smarthytte.schema.reservations.stats

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import no.slomic.smarthytte.schema.reservations.stats.ReservationStatsUtils.averageOrNullInt
import no.slomic.smarthytte.schema.reservations.stats.ReservationStatsUtils.averageRounded1OrNull
import no.slomic.smarthytte.schema.reservations.stats.ReservationStatsUtils.formatClock
import no.slomic.smarthytte.schema.reservations.stats.ReservationStatsUtils.formatMinutes
import no.slomic.smarthytte.schema.reservations.stats.ReservationStatsUtils.guestStatsComparator

class ReservationStatsUtilsTest :
    ShouldSpec({
        context("formatMinutes") {
            should("format minutes as HH:MM") {
                formatMinutes(90) shouldBe "01:30"
                formatMinutes(1500) shouldBe "25:00"
                formatMinutes(0) shouldBe "00:00"
            }

            should("handle null input") {
                formatMinutes(null) shouldBe null
            }

            should("handle negative minutes by using absolute value") {
                formatMinutes(-90) shouldBe "01:30"
            }
        }

        context("formatMinutes with showSign = true") {
            should("format positive minutes with + prefix") {
                formatMinutes(90, showSign = true) shouldBe "+01:30"
                formatMinutes(1500, showSign = true) shouldBe "+25:00"
            }

            should("format negative minutes with - prefix") {
                formatMinutes(-90, showSign = true) shouldBe "-01:30"
                formatMinutes(-1500, showSign = true) shouldBe "-25:00"
            }

            should("format zero minutes without prefix") {
                formatMinutes(0, showSign = true) shouldBe "00:00"
            }

            should("handle null input") {
                formatMinutes(null, showSign = true) shouldBe null
            }
        }

        context("formatClock") {
            should("format minutes of day as HH:MM with modulo 24") {
                formatClock(90) shouldBe "01:30"
                formatClock(1440) shouldBe "00:00"
                formatClock(1500) shouldBe "01:00"
            }

            should("handle null input") {
                formatClock(null) shouldBe null
            }
        }

        context("averageRounded1OrNull") {
            should("return null for empty list") {
                emptyList<Int>().averageRounded1OrNull() shouldBe null
            }

            should("calculate rounded average for list of integers") {
                listOf(1, 2).averageRounded1OrNull() shouldBe 1.5
                listOf(1, 2, 4).averageRounded1OrNull() shouldBe 2.3
                listOf(10).averageRounded1OrNull() shouldBe 10.0
            }
        }

        context("averageOrNullInt") {
            should("return null for empty list") {
                emptyList<Int>().averageOrNullInt() shouldBe null
            }

            should("calculate truncated integer average for list of integers") {
                listOf(1, 2).averageOrNullInt() shouldBe 1 // 1.5 -> 1
                listOf(1, 2, 4).averageOrNullInt() shouldBe 2 // 2.33 -> 2
                listOf(10).averageOrNullInt() shouldBe 10
            }
        }

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
