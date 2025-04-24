package no.slomic.smarthytte.reservations

import no.slomic.smarthytte.guests.GuestTable
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table

object ReservationGuestTable : Table(name = "reservation_guest") {
    val event: Column<EntityID<String>> =
        reference("reservation_id", ReservationTable.id, onDelete = ReferenceOption.CASCADE)
    val guest: Column<EntityID<String>> =
        reference("guest_id", GuestTable.id, onDelete = ReferenceOption.CASCADE)

    override val primaryKey = PrimaryKey(event, guest, name = "pk_reservation_guest")
}
