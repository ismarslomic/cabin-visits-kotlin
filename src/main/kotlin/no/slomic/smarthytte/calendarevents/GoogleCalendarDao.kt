package no.slomic.smarthytte.calendarevents

import kotlinx.datetime.Instant
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

object GoogleCalendarSyncTable : IntIdTable(name = "google_calendar_sync") {
    val syncToken: Column<String> = varchar(name = "sync_token", length = 100)
    val updatedTime: Column<Instant> = timestamp("updated_time")
}

class GoogleCalendarSyncEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<GoogleCalendarSyncEntity>(GoogleCalendarSyncTable)

    var syncToken: String by GoogleCalendarSyncTable.syncToken
    var updatedTime: Instant by GoogleCalendarSyncTable.updatedTime
}
