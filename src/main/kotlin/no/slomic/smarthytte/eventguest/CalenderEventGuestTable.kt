package no.slomic.smarthytte.eventguest

import no.slomic.smarthytte.calendar.CalendarEventTable
import no.slomic.smarthytte.guest.GuestTable
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table

object CalenderEventGuestTable : Table(name = "calendar_event_guest") {
    val event: Column<EntityID<String>> =
        reference("calendar_event_id", CalendarEventTable.id, onDelete = ReferenceOption.CASCADE)
    val guest: Column<EntityID<String>> =
        reference("guest_id", GuestTable.id, onDelete = ReferenceOption.CASCADE)

    override val primaryKey = PrimaryKey(event, guest, name = "pk_calendar_event_guest")
}
