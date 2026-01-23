package no.slomic.smarthytte.reservations

import no.slomic.smarthytte.guests.GuestTable
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.dao.id.EntityID

object ReservationGuestTable : Table(name = "reservation_guest") {
    val reservation: Column<EntityID<String>> =
        reference("reservation_id", ReservationTable.id, onDelete = ReferenceOption.CASCADE)
    val guest: Column<EntityID<String>> =
        reference("guest_id", GuestTable.id, onDelete = ReferenceOption.CASCADE)

    override val primaryKey = PrimaryKey(reservation, guest, name = "pk_reservation_guest")
}
