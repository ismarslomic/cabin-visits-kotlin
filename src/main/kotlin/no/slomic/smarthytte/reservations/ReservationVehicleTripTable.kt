package no.slomic.smarthytte.reservations

import no.slomic.smarthytte.vehicletrips.VehicleTripTable
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table

object ReservationVehicleTripTable : Table("reservation_vehicle_trip") {
    val reservationId: Column<EntityID<String>> =
        reference("reservation_id", ReservationTable.id, onDelete = ReferenceOption.CASCADE)
    val vehicleTripId: Column<EntityID<String>> =
        reference("vehicle_trip_id", VehicleTripTable.id, ReferenceOption.CASCADE)
    val tripType: Column<String> = varchar("trip_type", length = 20)

    override val primaryKey = PrimaryKey(reservationId, vehicleTripId)
}

enum class ReservationVehicleTripType {
    TO_CABIN,
    AT_CABIN,
    FROM_CABIN,
}
