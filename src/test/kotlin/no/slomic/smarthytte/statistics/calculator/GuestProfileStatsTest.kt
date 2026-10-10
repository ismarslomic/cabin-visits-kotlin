package no.slomic.smarthytte.statistics.calculator

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import no.slomic.smarthytte.guests.Gender
import no.slomic.smarthytte.guests.Guest
import no.slomic.smarthytte.reservations.Reservation
import no.slomic.smarthytte.statistics.model.GuestVisit
import no.slomic.smarthytte.statistics.model.VisitStatus

class GuestProfileStatsTest :
    ShouldSpec({
        val anna = Guest("anna", "Anna", "Hansen", 1990, gender = Gender.FEMALE)
        val bo = Guest("bo", "Bo", "Berg", 1985, gender = Gender.MALE)
        val family = Guest("fam", "Fam", "Ily", 1980, gender = Gender.MALE, isFamily = true)
        val guestsById = listOf(anna, bo, family).associateBy { it.id }
        val today = LocalDate(2026, 9, 29)

        fun reservation(id: String, start: LocalDate, end: LocalDate, vararg guestIds: String) = Reservation(
            id = id,
            startTime = start.atTime(0, 0).toInstant(TimeZone.UTC),
            endTime = end.atTime(0, 0).toInstant(TimeZone.UTC),
            guestIds = guestIds.toList(),
        )

        val past1 = reservation("1", LocalDate(2025, 6, 1), LocalDate(2025, 6, 5), "anna", "bo")
        val past2 = reservation("2", LocalDate(2026, 3, 10), LocalDate(2026, 3, 12), "anna")
        val ongoing = reservation("3", LocalDate(2026, 9, 27), LocalDate(2026, 10, 2), "bo", "anna")
        val upcoming = reservation("4", LocalDate(2026, 11, 6), LocalDate(2026, 11, 8), "anna")
        val reservations = listOf(upcoming, ongoing, past2, past1)

        context("calculateGuestProfileStats") {
            should("return null for an unknown guest") {
                calculateGuestProfileStats("unknown", today, reservations, guestsById) shouldBe null
            }

            should("list all visits of the guest oldest first with status, days and nights") {
                val profile = calculateGuestProfileStats("anna", today, reservations, guestsById)!!

                profile.visits shouldBe listOf(
                    GuestVisit(LocalDate(2025, 6, 1), LocalDate(2025, 6, 5), 5, 4, VisitStatus.COMPLETED),
                    GuestVisit(LocalDate(2026, 3, 10), LocalDate(2026, 3, 12), 3, 2, VisitStatus.COMPLETED),
                    GuestVisit(LocalDate(2026, 9, 27), LocalDate(2026, 10, 2), 6, 5, VisitStatus.ONGOING),
                    GuestVisit(LocalDate(2026, 11, 6), LocalDate(2026, 11, 8), 3, 2, VisitStatus.UPCOMING),
                )
            }

            should("not include visits of other guests") {
                val profile = calculateGuestProfileStats("bo", today, reservations, guestsById)!!

                profile.visits.map { it.startDate } shouldBe listOf(LocalDate(2025, 6, 1), LocalDate(2026, 9, 27))
            }

            should("use the visit before the ongoing one as last visit when the guest is at the cabin") {
                val guest = calculateGuestProfileStats("anna", today, reservations, guestsById)!!.guest

                guest.lastVisitDate shouldBe LocalDate(2026, 3, 12)
                guest.firstVisitDate shouldBe LocalDate(2025, 6, 1)
                guest.isFirstVisit shouldBe false
                guest.yearsVisited shouldBe listOf(2026, 2025)
                guest.allTime.totalVisits shouldBe 3 // the upcoming visit is not counted
            }

            should("use the last completed visit as last visit when the guest is not at the cabin") {
                val notAtCabin = listOf(past1, past2, upcoming)

                val guest = calculateGuestProfileStats("anna", today, notAtCabin, guestsById)!!.guest

                guest.lastVisitDate shouldBe LocalDate(2026, 3, 12)
                guest.isFirstVisit shouldBe false
            }

            should("mark the ongoing visit as first visit when the guest has no earlier visits") {
                val profile = calculateGuestProfileStats("bo", today, listOf(ongoing), guestsById)!!

                profile.guest.isFirstVisit shouldBe true
                profile.guest.lastVisitDate shouldBe null
            }

            should("treat a visit ending today as completed") {
                val leaving = reservation("5", LocalDate(2026, 9, 26), LocalDate(2026, 9, 29), "bo")

                val profile = calculateGuestProfileStats("bo", today, listOf(leaving), guestsById)!!

                profile.visits.single().status shouldBe VisitStatus.COMPLETED
                profile.guest.lastVisitDate shouldBe today
            }

            should("return an empty profile for a guest without visits") {
                val profile = calculateGuestProfileStats("fam", today, reservations, guestsById)!!

                profile.visits shouldBe emptyList()
                profile.guest.firstVisitDate shouldBe null
                profile.guest.lastVisitDate shouldBe null
                profile.guest.isFirstVisit shouldBe false
                profile.guest.allTime.totalVisits shouldBe 0
                profile.guest.allTime.visitsRank shouldBe null
            }

            should("rank the guest among all guests and among guests only") {
                val familyTrips = listOf(
                    reservation("10", LocalDate(2026, 1, 1), LocalDate(2026, 1, 3), "fam"),
                    reservation("11", LocalDate(2026, 2, 1), LocalDate(2026, 2, 3), "fam"),
                    reservation("12", LocalDate(2026, 3, 1), LocalDate(2026, 3, 3), "anna"),
                )

                val guest = calculateGuestProfileStats("anna", today, familyTrips, guestsById)!!.guest

                guest.allTime.visitsRank shouldBe 2
                guest.allTime.visitsRankAmongGuests shouldBe 1
                val familyStats = calculateGuestProfileStats("fam", today, familyTrips, guestsById)!!.guest
                familyStats.allTime.visitsRank shouldBe 1
                familyStats.allTime.visitsRankAmongGuests shouldBe null
            }

            should("set avatar url only when given") {
                calculateGuestProfileStats("anna", today, reservations, guestsById, "/api/guests/anna/avatar?v=1")!!
                    .guest.avatarUrl shouldBe "/api/guests/anna/avatar?v=1"
                calculateGuestProfileStats("anna", today, reservations, guestsById)!!.guest.avatarUrl shouldBe null
            }
        }
    })
