package no.slomic.smarthytte.calendar

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
import no.slomic.smarthytte.eventguest.CalenderEventGuestTable
import no.slomic.smarthytte.guest.GuestRepository
import no.slomic.smarthytte.guest.SqliteGuestRepository
import no.slomic.smarthytte.guest.guest
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

val now = Clock.System.now()

val event = CalendarEvent(
    id = "event1",
    start = now.truncatedToMillis(),
    end = now.plus(2.days).truncatedToMillis(),
    guestIds = listOf(),
    summary = "Test event",
    description = null,
    sourceCreated = now.minus(1.days).truncatedToMillis(),
    sourceUpdated = now.minus(5.hours).truncatedToMillis(),
)

class CalendarEventRepositoryTest :
    BaseDbTest({
        val repository: CalendarEventRepository = SqliteCalendarEventRepository()

        "add or update with new id should add new event" {
            val returnedEvent = repository.addOrUpdate(event)
            returnedEvent.shouldBeEqualToComparingFields(event)

            transaction {
                val allEvents: List<CalendarEventEntity> = CalendarEventEntity.all().toList()
                allEvents shouldHaveSize 1

                val readEvent: CalendarEventEntity = allEvents.first()
                readEvent.id.value shouldBe event.id
                readEvent.start shouldBe event.start
                readEvent.end shouldBe event.end
                readEvent.guests.shouldBeEmpty()
                readEvent.summary shouldBe event.summary
                readEvent.description shouldBe event.description
                readEvent.sourceCreated shouldBe event.sourceCreated
                readEvent.sourceUpdated shouldBe event.sourceUpdated
                readEvent.updated.shouldBeNull()
                readEvent.version shouldBe 1
            }
        }

        "add or update with existing id should update the existing event" {
            repository.addOrUpdate(event)

            val updatedEvent: CalendarEvent = event.copy(summary = "Test event 2")
            val returnedEvent: CalendarEvent = repository.addOrUpdate(updatedEvent)
            returnedEvent.shouldBeEqualToComparingFields(updatedEvent)

            transaction {
                val allEvents: List<CalendarEventEntity> = CalendarEventEntity.all().toList()
                allEvents shouldHaveSize 1

                val readEvent: CalendarEventEntity = allEvents.first()
                readEvent.id.value shouldBe updatedEvent.id
                readEvent.start shouldBe updatedEvent.start
                readEvent.end shouldBe updatedEvent.end
                readEvent.guests.shouldBeEmpty()
                readEvent.summary shouldBe updatedEvent.summary
                readEvent.description shouldBe updatedEvent.description
                readEvent.sourceCreated shouldBe updatedEvent.sourceCreated
                readEvent.sourceUpdated shouldBe updatedEvent.sourceUpdated
                readEvent.updated.shouldNotBeNull()
                readEvent.version shouldBe 2
            }
        }

        "add or update with existing id without property changes should not update the existing event" {
            repository.addOrUpdate(event)

            val updatedEvent: CalendarEvent = event
            val returnedEvent: CalendarEvent = repository.addOrUpdate(updatedEvent)
            returnedEvent.shouldBeEqualToComparingFields(updatedEvent)

            transaction {
                val allEvents: List<CalendarEventEntity> = CalendarEventEntity.all().toList()
                allEvents shouldHaveSize 1

                val readEvent: CalendarEventEntity = allEvents.first()
                readEvent.id.value shouldBe updatedEvent.id
                readEvent.start shouldBe updatedEvent.start
                readEvent.end shouldBe updatedEvent.end
                readEvent.guests.shouldBeEmpty()
                readEvent.summary shouldBe updatedEvent.summary
                readEvent.description shouldBe updatedEvent.description
                readEvent.sourceCreated shouldBe updatedEvent.sourceCreated
                readEvent.sourceUpdated shouldBe updatedEvent.sourceUpdated
                readEvent.updated.shouldBeNull()
                readEvent.version shouldBe 1
            }
        }

        "delete should remove existing event" {
            repository.addOrUpdate(event)
            repository.allEvents() shouldHaveSize 1
            repository.deleteEvent(event.id)
            repository.allEvents() shouldHaveSize 0
        }

        "reading event by existing id should read event" {
            repository.addOrUpdate(event)
            repository.eventById(event.id)!!.shouldBeEqualToComparingFields(event)
        }

        "reading event by non existing id should return null" {
            repository.eventById(event.id).shouldBeNull()
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
            repository.eventById(event.id)!!.shouldBeEqualToIgnoringFields(eventWithGuest, CalendarEvent::guestIds)

            transaction {
                val allEventGuests: List<ResultRow> = CalenderEventGuestTable.selectAll().toList()
                allEventGuests shouldHaveSize 2

                val firsGuest: ResultRow = allEventGuests.first()
                firsGuest[CalenderEventGuestTable.event] = event.id
                firsGuest[CalenderEventGuestTable.guest] = guest.id

                val lastGuest: ResultRow = allEventGuests.last()
                lastGuest[CalenderEventGuestTable.event] = event.id
                lastGuest[CalenderEventGuestTable.guest] = guest2.id
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
            repository.eventById(eventWithGuest.id).shouldNotBeNull()

            transaction {
                val allEventGuests: List<ResultRow> = CalenderEventGuestTable.selectAll().toList()
                allEventGuests shouldHaveSize 2
            }

            repository.deleteEvent(eventWithGuest.id)
            repository.eventById(eventWithGuest.id).shouldBeNull()

            transaction {
                val allEventGuests: List<ResultRow> = CalenderEventGuestTable.selectAll().toList()
                allEventGuests.shouldBeEmpty()
            }
        }
    })
