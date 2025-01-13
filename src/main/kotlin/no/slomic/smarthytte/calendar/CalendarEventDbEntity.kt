package no.slomic.smarthytte.calendar

import kotlinx.datetime.Instant
import no.slomic.smarthytte.common.BaseEntity
import no.slomic.smarthytte.common.BaseIdTable
import no.slomic.smarthytte.eventguest.CalenderEventGuestTable
import no.slomic.smarthytte.guest.GuestEntity
import org.jetbrains.exposed.dao.EntityClass
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

object CalendarEventTable : BaseIdTable<String>(name = "calendar_event") {
    override val id: Column<EntityID<String>> = varchar("id", length = 1024).entityId()
    val summary: Column<String?> = varchar(name = "summary", length = 1000).nullable()
    val description: Column<String?> = varchar(name = "description", length = 2000).nullable()
    val start: Column<Instant> = timestamp(name = "start")
    val end: Column<Instant> = timestamp(name = "end")
    val sourceCreated: Column<Instant?> = timestamp("source_created").nullable()
    val sourceUpdated: Column<Instant?> = timestamp("source_updated").nullable()
    override val primaryKey = PrimaryKey(id, name = "pk_calendar_event_id")
}

class CalendarEventEntity(id: EntityID<String>) : BaseEntity<String>(id, CalendarEventTable) {
    companion object : EntityClass<String, CalendarEventEntity>(CalendarEventTable)

    var start: Instant by CalendarEventTable.start
    var end: Instant by CalendarEventTable.end
    var summary: String? by CalendarEventTable.summary
    var description: String? by CalendarEventTable.description
    var guests by GuestEntity via CalenderEventGuestTable
    var sourceCreated: Instant? by CalendarEventTable.sourceCreated
    var sourceUpdated: Instant? by CalendarEventTable.sourceUpdated
}

fun daoToModel(dao: CalendarEventEntity) = CalendarEvent(
    id = dao.id.value,
    summary = dao.summary,
    description = dao.description,
    start = dao.start,
    end = dao.end,
    guestIds = listOf(),
    sourceCreated = dao.sourceCreated,
    sourceUpdated = dao.sourceUpdated,
)

object CalendarSyncTable : IntIdTable(name = "calendar_sync") {
    val syncToken: Column<String> = varchar(name = "sync_token", length = 100)
    val updated: Column<Instant> = timestamp("updated")
}

class CalendarSyncEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<CalendarSyncEntity>(CalendarSyncTable)

    var syncToken: String by CalendarSyncTable.syncToken
    var updated: Instant by CalendarSyncTable.updated
}
