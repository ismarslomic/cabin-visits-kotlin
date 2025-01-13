package no.slomic.smarthytte.calendar

import io.ktor.util.logging.KtorSimpleLogger
import io.ktor.util.logging.Logger
import kotlinx.datetime.Clock
import no.slomic.smarthytte.common.suspendTransaction
import no.slomic.smarthytte.guest.GuestEntity
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.SizedCollection

class SqliteCalendarEventRepository : CalendarEventRepository {
    private val logger: Logger = KtorSimpleLogger(SqliteCalendarEventRepository::class.java.name)
    private val synckTokenId: Int = 1

    override suspend fun allEvents(): List<CalendarEvent> = suspendTransaction {
        CalendarEventEntity.all().sortedBy { it.start }.map(::daoToModel)
    }

    override suspend fun eventById(id: String): CalendarEvent? = suspendTransaction {
        val entityId: EntityID<String> = EntityID(id, CalendarEventTable)
        val storedEvent: CalendarEventEntity? = CalendarEventEntity.findById(entityId)

        storedEvent?.let { daoToModel(it) }
    }

    override suspend fun addOrUpdate(calendarEvent: CalendarEvent): CalendarEvent = suspendTransaction {
        val entityId: EntityID<String> = EntityID(calendarEvent.id, CalendarEventTable)
        val storedEvent: CalendarEventEntity? = CalendarEventEntity.findById(entityId)

        if (storedEvent == null) {
            addEvent(calendarEvent)
        } else {
            updateEvent(calendarEvent)!!
        }
    }

    override suspend fun deleteEvent(id: String): Boolean = suspendTransaction {
        logger.info("Deleting event with id: $id")
        val entityId: EntityID<String> = EntityID(id, CalendarEventTable)
        val storedEvent: CalendarEventEntity? = CalendarEventEntity.findById(entityId)

        storedEvent?.delete()

        val wasDeleted: Boolean = storedEvent != null
        val summary: String? = storedEvent?.summary

        logger.info("Deleted event with id: $id and summary: $summary was successful: $wasDeleted")

        wasDeleted
    }

    override suspend fun syncToken(): String? =
        suspendTransaction { CalendarSyncEntity.findById(synckTokenId)?.syncToken }

    override suspend fun addOrUpdate(newSyncToken: String) {
        suspendTransaction {
            logger.info("Updating calendar sync token")

            val storedCalendarSync: CalendarSyncEntity? = CalendarSyncEntity.findById(synckTokenId)

            if (storedCalendarSync == null) {
                CalendarSyncEntity.new(synckTokenId) {
                    syncToken = newSyncToken
                    updated = Clock.System.now()
                }
            } else {
                storedCalendarSync.syncToken = newSyncToken
                storedCalendarSync.updated = Clock.System.now()
            }

            logger.info("Calendar sync token updated")
        }
    }

    override suspend fun deleteSyncToken() {
        suspendTransaction {
            logger.info("Deleting calendar sync token")
            val storedCalendarSync: CalendarSyncEntity? = CalendarSyncEntity.findById(synckTokenId)
            storedCalendarSync?.delete()
            logger.info("Calendar sync token deleted")
        }
    }

    private fun addEvent(event: CalendarEvent): CalendarEvent {
        logger.info("Adding event with id: ${event.id}")

        val eventGuests: List<GuestEntity> = event.guestIds.mapNotNull { id -> GuestEntity.findById(id) }

        val newEvent = CalendarEventEntity.new(event.id) {
            summary = event.summary
            description = event.description
            start = event.start
            end = event.end
            guests = SizedCollection(eventGuests)
            sourceCreated = event.sourceCreated
            sourceUpdated = event.sourceUpdated
            created = Clock.System.now()
        }

        logger.info("Added event with id: ${event.id} and summary: ${event.summary}")

        return daoToModel(newEvent)
    }

    /**
     * Note that actual database update is only performed if at least one column has changed the value, so
     * calling findByIdAndUpdate is not necessary doing any update if all columns have the same value in stored and new
     * event.
     */
    private fun updateEvent(event: CalendarEvent): CalendarEvent? {
        logger.info("Updating event with id: ${event.id}")

        val eventGuests: List<GuestEntity> = event.guestIds.mapNotNull { id -> GuestEntity.findById(id) }

        val updatedEvent: CalendarEventEntity = CalendarEventEntity.findById(event.id) ?: return null

        with(updatedEvent) {
            summary = event.summary
            description = event.description
            start = event.start
            end = event.end
            sourceCreated = event.sourceCreated
            sourceUpdated = event.sourceUpdated
        }

        val isDirty: Boolean = updatedEvent.writeValues.isNotEmpty()

        if (isDirty) {
            updatedEvent.version = updatedEvent.version.inc()
            updatedEvent.updated = Clock.System.now()
        }

        // This triggers flushing changes and thus empties the writeValues, so we keep it as the last change
        updatedEvent.guests = SizedCollection(eventGuests)

        if (isDirty) {
            logger.info("Updated event with id: ${event.id} and summary: ${event.summary}")
        } else {
            logger.info("No changes detected for event with id: ${event.id} and summary: ${event.summary}")
        }

        return daoToModel(updatedEvent)
    }
}
