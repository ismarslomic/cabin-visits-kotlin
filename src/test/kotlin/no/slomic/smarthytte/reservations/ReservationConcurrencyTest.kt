package no.slomic.smarthytte.reservations

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import no.slomic.smarthytte.auth.testGuest
import no.slomic.smarthytte.calendar.CalendarOperation
import no.slomic.smarthytte.calendar.CalendarOutboxTable
import no.slomic.smarthytte.checkinouts.CheckIn
import no.slomic.smarthytte.checkinouts.CheckInOutSource
import no.slomic.smarthytte.guests.SqliteGuestRepository
import no.slomic.smarthytte.utils.FileDatabaseTestSupport
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

private val start = Instant.parse("2026-07-10T16:00:00Z")
private val end = Instant.parse("2026-07-12T13:00:00Z")
private const val WRITERS = 8
private const val ROUNDS = 10
private const val READERS = 50
private const val READ_SEED = 300
private const val NANOS_PER_MS = 1_000_000
private const val MAX_READ_BURST_MS = 2_000

/**
 * Concurrent writes on a file database with the production `DataSource` setup, which locks like production. Writers
 * queue on the write lock (`BEGIN IMMEDIATE` and a busy timeout, decision 58), so a loser sees the state the winner
 * committed (an overlap) and never an `SQLITE_BUSY` exception.
 */
class ReservationConcurrencyTest :
    StringSpec({
        val database = FileDatabaseTestSupport()

        beforeTest {
            database.setup()
            SqliteGuestRepository().addOrUpdate(testGuest("a"))
        }
        afterTest { database.delete() }

        val repository = SqliteReservationRepository()

        fun <T> concurrently(count: Int, block: suspend (Int) -> T): List<T> = runBlocking {
            (1..count).map { index -> async(Dispatchers.Default) { block(index) } }.awaitAll()
        }

        "concurrent creates of overlapping reservations should give one Created and the others Overlap" {
            repeat(ROUNDS) {
                transaction { CalendarOutboxTable.deleteWhere { CalendarOutboxTable.id greaterEq 0 } }
                repository.allReservations().forEach { repository.deleteReservation(it.id, it.version, "admin") }

                val outcomes = concurrently(WRITERS) { repository.createReservation(start, end, listOf("a"), "admin") }

                outcomes.count { it is ReservationCreateOutcome.Created } shouldBe 1
                outcomes.count { it === ReservationCreateOutcome.Overlap } shouldBe WRITERS - 1
                repository.allReservations() shouldHaveSize 1
                transaction {
                    CalendarOutboxTable.selectAll().filter {
                        it[CalendarOutboxTable.operation] ==
                            CalendarOperation.INSERT
                    }
                } shouldHaveSize 1
            }
        }

        "concurrent creates of reservations that do not overlap should all succeed" {
            val outcomes = concurrently(WRITERS) { index ->
                val from = start + (index * 3).days
                repository.createReservation(from, from + 2.days, listOf("a"), "admin")
            }

            outcomes.forEach { it.shouldBeInstanceOf<ReservationCreateOutcome.Created>() }
            repository.allReservations() shouldHaveSize WRITERS
        }

        "reads should not fail while reservations are written" {
            val results = concurrently(WRITERS) { index ->
                if (index % 2 == 0) {
                    val from = start + (index * 3).days
                    repository.createReservation(from, from + 2.days, listOf("a"), "admin")
                } else {
                    repository.allReservations()
                }
            }

            results.filterIsInstance<ReservationCreateOutcome.Created>() shouldHaveSize WRITERS / 2
        }

        "concurrent reads should not queue behind each other" {
            repeat(READ_SEED) {
                val from = start + (it * 3).days
                repository.createReservation(from, from + 1.days, listOf("a"), "admin")
            }

            val startedAt = System.nanoTime()
            val reads = concurrently(READERS) { repository.allReservations() }
            val elapsedMs = (System.nanoTime() - startedAt) / NANOS_PER_MS

            reads.forEach { it shouldHaveSize READ_SEED }
            // Reads on the write DataSource queued on the write lock: 50 reads took up to 4 s. On the read DataSource
            // they took about 0.13 s, so this bound has a wide margin for a slow CI machine.
            (elapsedMs < MAX_READ_BURST_MS) shouldBe true
        }

        "a time change racing the check-in job should never leave derived data of the old times" {
            repeat(ROUNDS) { round ->
                val from = start + (round * 10).days
                val reservation = repository.createReservation(from, from + 2.days, listOf("a"), "admin")
                    .shouldBeInstanceOf<ReservationCreateOutcome.Created>().reservation
                val checkIn = CheckIn(from, CheckInOutSource.CHECK_IN_SENSOR, "sensor")

                concurrently(2) { index ->
                    if (index == 1) {
                        repository.setCheckIn(checkIn, reservation.id, reservation.startTime, reservation.endTime)
                    } else {
                        repository.updateReservation(
                            reservation.id,
                            reservation.version,
                            from + 1.hours,
                            from + 3.days,
                            listOf("a"),
                            "admin",
                        )
                    }
                }

                // The check-in job computed it from the old times: it is either skipped or cleared by the update
                repository.reservationById(reservation.id)?.checkIn.shouldBeNull()
            }
        }

        "a delete racing the check-in job should not fail and should leave nothing behind" {
            val reservation = repository.createReservation(start, end, listOf("a"), "admin")
                .shouldBeInstanceOf<ReservationCreateOutcome.Created>().reservation
            val checkIn = CheckIn(start, CheckInOutSource.CHECK_IN_SENSOR, "sensor")

            concurrently(2) { index ->
                if (index == 1) {
                    repository.setCheckIn(checkIn, reservation.id, reservation.startTime, reservation.endTime)
                } else {
                    repository.deleteReservation(reservation.id, reservation.version, "admin")
                }
            }

            repository.allReservations().shouldBeEmpty()
        }
    })
