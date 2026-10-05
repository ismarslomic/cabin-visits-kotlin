package no.slomic.smarthytte.statistics

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import no.slomic.smarthytte.checkinouts.CABIN_CITY_NAME
import no.slomic.smarthytte.checkinouts.HOME_CITY_NAME
import no.slomic.smarthytte.common.utcDateNow
import no.slomic.smarthytte.guests.Gender
import no.slomic.smarthytte.guests.Guest
import no.slomic.smarthytte.guests.GuestRepository
import no.slomic.smarthytte.guests.SqliteGuestRepository
import no.slomic.smarthytte.reservations.Reservation
import no.slomic.smarthytte.reservations.ReservationRepository
import no.slomic.smarthytte.reservations.ReservationVehicleTripType
import no.slomic.smarthytte.reservations.SqliteReservationRepository
import no.slomic.smarthytte.statistics.model.FunFact
import no.slomic.smarthytte.utils.TestDbSetup
import no.slomic.smarthytte.vehicletrips.SqliteVehicleTripRepository
import no.slomic.smarthytte.vehicletrips.VehicleTripRepository
import no.slomic.smarthytte.vehicletrips.createTrip
import kotlin.time.Duration.Companion.minutes

/**
 * Reservations are anchored on [utcDateNow] because [Reservation.hasStarted] and [Reservation.hasEnded] use the system
 * clock. They start at midnight UTC of a date, so the start date (and its year) never depends on the time of day.
 */
class StatsServiceTest :
    ShouldSpec({
        val testDbSetup = TestDbSetup()
        val dataStart = LocalDate(2020, 9, 1)

        beforeEach { testDbSetup.setupDb() }
        afterEach { testDbSetup.teardownDb() }

        val guestRepository: GuestRepository = SqliteGuestRepository()
        val reservationRepository: ReservationRepository = SqliteReservationRepository()
        val vehicleTripRepository: VehicleTripRepository = SqliteVehicleTripRepository()
        val service = StatsService(reservationRepository, guestRepository, dataStart)

        val anna = Guest("anna", "Anna", "Test", 1990, gender = Gender.FEMALE)
        val bjorn = Guest("bjorn", "Bjorn", "Test", 1985, gender = Gender.MALE, isFamily = true)

        fun daysFromToday(days: Int): LocalDate = utcDateNow().plus(DatePeriod(days = days))

        suspend fun addGuests(vararg guests: Guest) = guests.forEach { guestRepository.addOrUpdate(it) }

        suspend fun addReservation(id: String, start: LocalDate, end: LocalDate, vararg guests: Guest) {
            val reservation = Reservation(
                id = id,
                startTime = start.atStartOfDayIn(TimeZone.UTC),
                endTime = end.atStartOfDayIn(TimeZone.UTC),
                guestIds = guests.map { it.id },
            )
            reservationRepository.addOrUpdate(reservation)
        }

        suspend fun addTrip(
            reservationId: String,
            date: LocalDate,
            type: ReservationVehicleTripType,
            durationMinutes: Int,
            energyConsumption: Double = 0.0,
            distanceKm: Double = 0.0,
            energyRegeneratedKwh: Double = 0.0,
        ) {
            val (startCity, endCity) = when (type) {
                ReservationVehicleTripType.FROM_CABIN -> CABIN_CITY_NAME to HOME_CITY_NAME
                else -> HOME_CITY_NAME to CABIN_CITY_NAME
            }
            val trip = createTrip(startCity, endCity, "${date}T10:00:00Z", "${date}T12:00:00Z").copy(
                duration = durationMinutes.minutes,
                averageEnergyConsumption = energyConsumption,
                distance = distanceKm,
                energyRegenerated = energyRegeneratedKwh,
            )
            vehicleTripRepository.addOrUpdate(trip)
            reservationRepository.addVehicleTripLink(reservationId, trip.id, type)
        }

        /** One reservation per given duration in June last year, each with a trip to the cabin of that duration. */
        suspend fun addPreviousYearTripsToCabin(vararg durationsMinutes: Int) {
            val lastYear = utcDateNow().year - 1
            durationsMinutes.forEachIndexed { index, minutes ->
                val start = LocalDate(lastYear, 6, index + 1)
                addReservation("prev$index", start, start.plus(DatePeriod(days = 1)), anna)
                addTrip("prev$index", start, ReservationVehicleTripType.TO_CABIN, minutes)
            }
        }

        context("getLiveStats without reservations") {
            should("return empty facts except the cabin totals and years of ownership") {
                val stats = service.getLiveStats()

                stats.isOccupied shouldBe false
                stats.currentReservation.shouldBeNull()
                stats.nextReservation.shouldBeNull()
                stats.allTimeVisits shouldBe 0
                stats.guestFunFacts.shouldBeEmpty()
                stats.nextVisitFunFacts.shouldBeEmpty()
                stats.cabinFunFacts.map { it.text } shouldContain
                    "Siden sept 2020: 0 besøk, 0 netter og 0 ulike gjester"
            }
        }

        context("getLiveStats during a stay") {
            should("report the current reservation with totals and remaining nights") {
                addGuests(anna, bjorn)
                addReservation("r1", daysFromToday(0), daysFromToday(4), anna, bjorn)

                val stats = service.getLiveStats()

                stats.isOccupied shouldBe true
                stats.nextReservation.shouldBeNull()
                stats.allTimeVisits shouldBe 1
                stats.allTimeNights shouldBe 4
                stats.allTimeUniqueGuests shouldBe 2
                val current = stats.currentReservation.shouldNotBeNull()
                current.remainingNights shouldBe 4
                current.guests.map { it.guestId } shouldBe listOf("anna", "bjorn")
            }

            should("create guest facts for the current reservation only") {
                addGuests(anna, bjorn)
                addReservation("r1", daysFromToday(0), daysFromToday(4), anna, bjorn)
                addReservation("r2", daysFromToday(10), daysFromToday(12), bjorn)

                val facts = service.getLiveStats().guestFunFacts

                facts.first { it.priority == 100 }.guestId shouldBe "anna"
                facts shouldContain FunFact(null, "4 netter igjen av oppholdet", 30)
                facts.none { it.text.contains("Neste besøk") } shouldBe true
            }

            should("create cabin facts from the totals and the year statistics") {
                addGuests(anna, bjorn)
                addReservation("r1", daysFromToday(0), daysFromToday(4), anna, bjorn)

                val texts = service.getLiveStats().cabinFunFacts.map { it.text }

                texts shouldContain "Siden sept 2020: 1 besøk, 4 netter og 2 ulike gjester"
                texts shouldContain "2 nye gjester på hytta i år"
                texts shouldContain "Årets lengste opphold: 4 netter"
                texts shouldContain "I snitt 2,0 personer per besøk i år"
            }
        }

        context("getLiveStats between stays") {
            should("report the next reservation and exclude it from the all-time totals") {
                addGuests(anna)
                addReservation("r1", daysFromToday(-10), daysFromToday(-8), anna)
                addReservation("r2", daysFromToday(1), daysFromToday(3), anna)

                val stats = service.getLiveStats()

                stats.isOccupied shouldBe false
                stats.currentReservation.shouldBeNull()
                stats.allTimeVisits shouldBe 1
                stats.allTimeNights shouldBe 2
                stats.guestFunFacts.shouldBeEmpty()
                val next = stats.nextReservation.shouldNotBeNull()
                next.daysUntil shouldBe 1
                next.guests.map { it.guestId } shouldBe listOf("anna")
            }

            should("create next visit facts") {
                addGuests(anna)
                addReservation("r1", daysFromToday(1), daysFromToday(3), anna)

                val facts = service.getLiveStats().nextVisitFunFacts

                facts shouldContain FunFact(null, "Neste besøk starter i morgen", 70)
                facts shouldContain FunFact("anna", "Anna kommer på hytta for første gang! 🎉", 45)
            }

            should("count down to the next visit only while the cabin is occupied") {
                addGuests(anna)
                addReservation("r1", daysFromToday(0), daysFromToday(2), anna)
                addReservation("r2", daysFromToday(7), daysFromToday(9), anna)

                service.getLiveStats().nextVisitFunFacts shouldContain FunFact(null, "Neste besøk om 7 dager", 45)
            }
        }

        context("year and month statistics") {
            should("list the years with started reservations") {
                addGuests(anna)
                addReservation("r1", daysFromToday(0), daysFromToday(2), anna)
                addReservation("r2", daysFromToday(30), daysFromToday(32), anna)

                service.getAvailableYears() shouldBe listOf(utcDateNow().year)
            }

            should("summarise the current year") {
                addGuests(anna)
                addReservation("r1", daysFromToday(0), daysFromToday(3), anna)

                val stats = service.getCurrentYearStats()

                stats.year shouldBe utcDateNow().year
                stats.visits shouldBe 1
                stats.totalNights shouldBe 3
                stats.totalDistanceKm.shouldBeNull()
            }

            should("build year statistics with all twelve months") {
                addGuests(anna, bjorn)
                addReservation("r1", daysFromToday(0), daysFromToday(2), anna, bjorn)

                val stats = service.getYearStats(utcDateNow().year)

                stats.visits.totalVisits shouldBe 1
                stats.visits.avgGroupSize shouldBe 2.0
                stats.nights.maxNights shouldBe 2
                stats.newGuests shouldHaveSize 2
                stats.months shouldHaveSize 12
                stats.drivingTime.shouldBeNull()
                stats.ev.shouldBeNull()
            }

            should("build month statistics for the month of the reservation") {
                addGuests(anna)
                addReservation("r1", daysFromToday(0), daysFromToday(1), anna)
                val today = utcDateNow()

                val stats = service.getMonthStats(today.year, today.month.ordinal + 1)

                stats.visits.totalVisits shouldBe 1
                stats.guests.map { it.guestId } shouldBe listOf("anna")
            }
        }

        context("getLiveStats driving facts") {
            should("report a driving record when the trip to the cabin is faster than all earlier trips") {
                addGuests(anna)
                addPreviousYearTripsToCabin(100, 110, 120, 130, 140)
                addReservation("current", daysFromToday(0), daysFromToday(2), anna)
                addTrip("current", daysFromToday(0), ReservationVehicleTripType.TO_CABIN, 80)

                service.getLiveStats().guestFunFacts shouldContain
                    FunFact(null, "Ny rekord! Raskeste tur til hytta: 1 t 20 min", 85)
            }

            should("not report a record when an earlier trip was faster or there are too few earlier trips") {
                addGuests(anna)
                addPreviousYearTripsToCabin(100, 110, 120, 130, 140)
                addReservation("current", daysFromToday(0), daysFromToday(2), anna)
                addTrip("current", daysFromToday(0), ReservationVehicleTripType.TO_CABIN, 105)

                service.getLiveStats().guestFunFacts.none { it.text.startsWith("Ny rekord") } shouldBe true
            }

            should("not report a record with fewer than five earlier trips") {
                addGuests(anna)
                addPreviousYearTripsToCabin(100, 110)
                addReservation("current", daysFromToday(0), daysFromToday(2), anna)
                addTrip("current", daysFromToday(0), ReservationVehicleTripType.TO_CABIN, 60)

                service.getLiveStats().guestFunFacts.none { it.text.startsWith("Ny rekord") } shouldBe true
            }

            should("compare the driving time with last year in the cabin facts") {
                addGuests(anna)
                addPreviousYearTripsToCabin(120)
                addReservation("current", daysFromToday(0), daysFromToday(2), anna)
                addTrip("current", daysFromToday(0), ReservationVehicleTripType.TO_CABIN, 80)

                service.getLiveStats().cabinFunFacts.map { it.text }
                    .first { it.startsWith("Turen til hytta") } shouldStartWith
                    "Turen til hytta er 40 min kortere enn i"
            }
        }

        context("getLiveStats avatars") {
            should("link to the avatar only for guests with an image") {
                guestRepository.addOrUpdate(anna, byteArrayOf(1, 2, 3))
                addGuests(bjorn)
                addReservation("r1", daysFromToday(0), daysFromToday(2), anna, bjorn)

                val guests = service.getLiveStats().currentReservation.shouldNotBeNull().guests.associateBy {
                    it.guestId
                }

                guests.getValue("anna").avatarUrl.shouldNotBeNull() shouldStartWith "/api/guests/anna/avatar?v="
                guests.getValue("bjorn").avatarUrl.shouldBeNull()
            }
        }

        context("getYearStats and getMonthStats over several years") {
            should("compare with the previous year and find the busiest month") {
                val lastYear = utcDateNow().year - 1
                addGuests(anna)
                addReservation("p1", LocalDate(lastYear, 6, 10), LocalDate(lastYear, 6, 12), anna)
                addReservation("p2", LocalDate(lastYear, 6, 20), LocalDate(lastYear, 6, 22), anna)
                addReservation("current", daysFromToday(0), daysFromToday(1), anna)

                service.getYearStats(utcDateNow().year).visits.comparedToPreviousYear shouldBe -1

                val previous = service.getYearStats(lastYear)
                previous.visits.totalVisits shouldBe 2
                previous.visits.avgMonthlyVisits shouldBe 0.2
                previous.visits.monthMostVisits.shouldNotBeNull().monthNumber shouldBe 6
                previous.visits.monthMostVisits.shouldNotBeNull().visitCount shouldBe 2
                service.getMonthStats(lastYear, 6).visits.totalVisits shouldBe 2
                service.getMonthStats(lastYear, 7).visits.totalVisits shouldBe 0
            }

            should("sum the energy use and distance of the trips to and from the cabin") {
                val lastYear = utcDateNow().year - 1
                val start = LocalDate(lastYear, 6, 10)
                addGuests(anna)
                addReservation("p1", start, start.plus(DatePeriod(days = 2)), anna)
                addTrip("p1", start, ReservationVehicleTripType.TO_CABIN, 100, 20.0, 100.0, 3.0)
                addTrip(
                    "p1",
                    start.plus(DatePeriod(days = 2)),
                    ReservationVehicleTripType.FROM_CABIN,
                    90,
                    10.0,
                    100.0,
                    2.0,
                )

                val stats = service.getYearStats(lastYear)

                val ev = stats.ev.shouldNotBeNull()
                ev.totalEnergyConsumedKwh shouldBe 30.0
                ev.avgEnergyConsumptionKwhPer100km shouldBe 15.0
                ev.totalEnergyRegeneratedKwh shouldBe 5.0
                stats.drivingDistance.shouldNotBeNull().totalToCabinKm shouldBe 100.0
                stats.drivingTime.shouldNotBeNull().avgToCabinMinutes shouldBe 100
                stats.drivingTime.shouldNotBeNull().avgFromCabinMinutes shouldBe 90
            }
        }

        context("getGuestStats") {
            should("rank guests, and group them by gender and age") {
                val year = utcDateNow().year
                val carl = Guest("carl", "Carl", "Test", (year - 10).toShort(), gender = Gender.MALE)
                addGuests(
                    anna.copy(birthYear = (year - 40).toShort()),
                    bjorn.copy(birthYear = (year - 45).toShort()),
                    carl,
                )
                addReservation("r1", daysFromToday(-20), daysFromToday(-18), anna, bjorn)
                addReservation("r2", daysFromToday(-10), daysFromToday(-7), anna)

                val stats = service.getGuestStats()

                stats.topGuestsByVisits.map { it.guestId to it.totalVisits } shouldBe listOf("anna" to 2, "bjorn" to 1)
                stats.topGuestsByDays.first().guestId shouldBe "anna"
                stats.genderDistribution.maleCount shouldBe 2
                stats.genderDistribution.femaleCount shouldBe 1
                stats.genderDistribution.malePercent shouldBe 66.7
                stats.genderDistribution.femalePercent shouldBe 33.3
                stats.ageGroups.associate { it.label to it.guestCount } shouldBe mapOf(
                    "0-12" to 1,
                    "13-17" to 0,
                    "18-30" to 0,
                    "31-50" to 2,
                    "51+" to 0,
                )
            }

            should("return empty rankings without reservations") {
                val stats = service.getGuestStats()

                stats.topGuestsByVisits.shouldBeEmpty()
                stats.genderDistribution.maleCount shouldBe 0
            }
        }
    })
