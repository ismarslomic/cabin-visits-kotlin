package no.slomic.smarthytte.reservations

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.equality.shouldBeEqualToComparingFields
import io.kotest.matchers.equality.shouldBeEqualToIgnoringFields
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.datetime.Clock
import no.slomic.smarthytte.BaseDbTest
import no.slomic.smarthytte.common.truncatedToMillis
import no.slomic.smarthytte.guest.GuestRepository
import no.slomic.smarthytte.guest.SqliteGuestRepository
import no.slomic.smarthytte.guest.guest
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

val now = Clock.System.now()

val event = Reservation(
    id = "event1",
    startTime = now.truncatedToMillis(),
    endTime = now.plus(2.days).truncatedToMillis(),
    guestIds = listOf(),
    summary = "Test event",
    description = null,
    sourceCreatedTime = now.minus(1.days).truncatedToMillis(),
    sourceUpdatedTime = now.minus(5.hours).truncatedToMillis(),
)

class ReservationRepositoryTest :
    BaseDbTest({
        val repository: ReservationRepository = SqliteReservationRepository()

        "add or update with new id should add new event" {
            val returnedEvent = repository.addOrUpdate(event)
            returnedEvent.shouldBeEqualToComparingFields(event)

            transaction {
                val allEvents: List<ReservationEntity> = ReservationEntity.all().toList()
                allEvents shouldHaveSize 1

                val readEvent: ReservationEntity = allEvents.first()
                readEvent.id.value shouldBe event.id
                readEvent.startTime shouldBe event.startTime
                readEvent.endTime shouldBe event.endTime
                readEvent.guests.shouldBeEmpty()
                readEvent.summary shouldBe event.summary
                readEvent.description shouldBe event.description
                readEvent.sourceCreatedTime shouldBe event.sourceCreatedTime
                readEvent.sourceUpdatedTime shouldBe event.sourceUpdatedTime
                readEvent.updatedTime.shouldBeNull()
                readEvent.version shouldBe 1
            }
        }

        "add or update with existing id should update the existing event" {
            repository.addOrUpdate(event)

            val updatedEvent: Reservation = event.copy(summary = "Test event 2")
            val returnedEvent: Reservation = repository.addOrUpdate(updatedEvent)
            returnedEvent.shouldBeEqualToComparingFields(updatedEvent)

            transaction {
                val allEvents: List<ReservationEntity> = ReservationEntity.all().toList()
                allEvents shouldHaveSize 1

                val readEvent: ReservationEntity = allEvents.first()
                readEvent.id.value shouldBe updatedEvent.id
                readEvent.startTime shouldBe updatedEvent.startTime
                readEvent.endTime shouldBe updatedEvent.endTime
                readEvent.guests.shouldBeEmpty()
                readEvent.summary shouldBe updatedEvent.summary
                readEvent.description shouldBe updatedEvent.description
                readEvent.sourceCreatedTime shouldBe updatedEvent.sourceCreatedTime
                readEvent.sourceUpdatedTime shouldBe updatedEvent.sourceUpdatedTime
                readEvent.updatedTime.shouldNotBeNull()
                readEvent.version shouldBe 2
            }
        }

        "add or update with existing id without property changes should not update the existing event" {
            repository.addOrUpdate(event)

            val updatedEvent: Reservation = event
            val returnedEvent: Reservation = repository.addOrUpdate(updatedEvent)
            returnedEvent.shouldBeEqualToComparingFields(updatedEvent)

            transaction {
                val allEvents: List<ReservationEntity> = ReservationEntity.all().toList()
                allEvents shouldHaveSize 1

                val readEvent: ReservationEntity = allEvents.first()
                readEvent.id.value shouldBe updatedEvent.id
                readEvent.startTime shouldBe updatedEvent.startTime
                readEvent.endTime shouldBe updatedEvent.endTime
                readEvent.guests.shouldBeEmpty()
                readEvent.summary shouldBe updatedEvent.summary
                readEvent.description shouldBe updatedEvent.description
                readEvent.sourceCreatedTime shouldBe updatedEvent.sourceCreatedTime
                readEvent.sourceUpdatedTime shouldBe updatedEvent.sourceUpdatedTime
                readEvent.updatedTime.shouldBeNull()
                readEvent.version shouldBe 1
            }
        }

        "delete should remove existing event" {
            repository.addOrUpdate(event)
            repository.allReservations() shouldHaveSize 1
            repository.deleteReservation(event.id)
            repository.allReservations() shouldHaveSize 0
        }

        "reading event by existing id should read event" {
            repository.addOrUpdate(event)
            repository.reservationById(event.id)!!.shouldBeEqualToComparingFields(event)
        }

        "reading event by non existing id should return null" {
            repository.reservationById(event.id).shouldBeNull()
        }

        "adding guests to the event should store guests to intermediate table" {
            val guestRepository: GuestRepository = SqliteGuestRepository()
            guestRepository.addOrUpdate(guest)
            val guest2 = guest.copy(id = "john2", firstName = "John2", lastName = "Doe2")
            guestRepository.addOrUpdate(guest2)

            val eventWithGuest = event.copy(
                guestIds = listOf(guest.id, guest2.id),
            )
            repository.addOrUpdate(eventWithGuest)

            // We are currently not loading the guests when reading from database
            repository.reservationById(event.id)!!.shouldBeEqualToIgnoringFields(eventWithGuest, Reservation::guestIds)

            transaction {
                val allEventGuests: List<ResultRow> = ReservationGuestTable.selectAll().toList()
                allEventGuests shouldHaveSize 2

                val firsGuest: ResultRow = allEventGuests.first()
                firsGuest[ReservationGuestTable.event] = event.id
                firsGuest[ReservationGuestTable.guest] = guest.id

                val lastGuest: ResultRow = allEventGuests.last()
                lastGuest[ReservationGuestTable.event] = event.id
                lastGuest[ReservationGuestTable.guest] = guest2.id
            }
        }

        "delete should remove event guests from the intermediate table (cascade)" {
            val guestRepository: GuestRepository = SqliteGuestRepository()
            guestRepository.addOrUpdate(guest)
            val guest2 = guest.copy(id = "john2", firstName = "John2", lastName = "Doe2")
            guestRepository.addOrUpdate(guest2)

            val eventWithGuest = event.copy(
                guestIds = listOf(guest.id, guest2.id),
            )
            repository.addOrUpdate(eventWithGuest)
            repository.reservationById(eventWithGuest.id).shouldNotBeNull()

            transaction {
                val allEventGuests: List<ResultRow> = ReservationGuestTable.selectAll().toList()
                allEventGuests shouldHaveSize 2
            }

            repository.deleteReservation(eventWithGuest.id)
            repository.reservationById(eventWithGuest.id).shouldBeNull()

            transaction {
                val allEventGuests: List<ResultRow> = ReservationGuestTable.selectAll().toList()
                allEventGuests.shouldBeEmpty()
            }
        }
    })
