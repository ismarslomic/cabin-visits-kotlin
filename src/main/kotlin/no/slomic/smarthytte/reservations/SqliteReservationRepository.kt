package no.slomic.smarthytte.reservations

import io.ktor.util.logging.KtorSimpleLogger
import io.ktor.util.logging.Logger
import no.slomic.smarthytte.checkinouts.CheckIn
import no.slomic.smarthytte.checkinouts.CheckOut
import no.slomic.smarthytte.common.PersistenceResult
import no.slomic.smarthytte.common.markCreated
import no.slomic.smarthytte.common.markUpdated
import no.slomic.smarthytte.common.suspendTransaction
import no.slomic.smarthytte.common.truncatedToMillis
import no.slomic.smarthytte.guests.GuestEntity
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.dao.with
import org.jetbrains.exposed.v1.jdbc.SizedCollection
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.select
import kotlin.time.Clock
import kotlin.time.Instant

@Suppress("TooManyFunctions")
class SqliteReservationRepository : ReservationRepository {
    private val logger: Logger = KtorSimpleLogger(SqliteReservationRepository::class.java.name)

    override suspend fun allReservations(): List<Reservation> = suspendTransaction {
        val reservationEntities = ReservationEntity.all()
            .with(ReservationEntity::guests, ReservationEntity::vehicleTrips)
            .sortedBy { it.startTime }

        val tripTypes = fetchTripTypes(reservationEntities.map { it.id })

        reservationEntities.map { daoToModel(it, tripTypes) }
    }

    override suspend fun reservationById(id: String): Reservation? = suspendTransaction {
        val entityId = EntityID(id, ReservationTable)
        val storedReservation = ReservationEntity.findById(entityId)

        storedReservation?.let {
            val tripTypes = fetchTripTypes(listOf(it.id))
            daoToModel(it, tripTypes)
        }
    }

    override suspend fun addOrUpdate(reservation: Reservation, actor: String): PersistenceResult = suspendTransaction {
        val entityId: EntityID<String> = EntityID(reservation.id, ReservationTable)
        val storedReservation: ReservationEntity? = ReservationEntity.findById(entityId)

        if (storedReservation == null) {
            addReservation(reservation, actor)
        } else {
            updateReservation(reservation, actor)
        }
    }

    override suspend fun addVehicleTripLink(
        reservationId: String,
        vehicleTripId: String,
        type: ReservationVehicleTripType,
        startTime: Instant,
        endTime: Instant,
    ): PersistenceResult = suspendTransaction {
        // SQLite's OR IGNORE does not suppress a foreign key violation, so a deleted or changed reservation is
        // detected here
        if (!isSnapshotCurrent(reservationId, startTime, endTime)) {
            logger.trace("Reservation with id: {} is gone or changed, no trip link added", reservationId)
            return@suspendTransaction PersistenceResult.NO_ACTION
        }

        logger.trace(
            "Adding link between vehicle trip with id: {} (type: {}) and the Reservation with id: {}",
            vehicleTripId,
            type,
            reservationId,
        )

        // Since ReservationVehicleTripTable is a pure join table, and we don't have any Entity,
        // we use the insertIgnore, which either inserts the row or ignores the inserting if the unique
        // constraint is violated
        val result = ReservationVehicleTripTable.insertIgnore {
            it[this.reservationId] = reservationId
            it[this.vehicleTripId] = vehicleTripId
            it[this.tripType] = type.name
        }

        if (result.insertedCount > 0) {
            logger.trace("Link between vehicle trip and reservation created successfully")
            PersistenceResult.ADDED
        } else {
            logger.trace("Vehicle trip already linked to the reservation")
            PersistenceResult.NO_ACTION
        }
    }

    override suspend fun deleteReservation(id: String): PersistenceResult = suspendTransaction {
        logger.trace("Deleting reservation with id: $id")

        val entityId: EntityID<String> = EntityID(id, ReservationTable)
        val storedReservation: ReservationEntity? = ReservationEntity.findById(entityId)

        storedReservation?.delete()

        val wasDeleted: Boolean = storedReservation != null
        val summary: String? = storedReservation?.summary

        if (wasDeleted) {
            logger.trace("Deleted reservation with id: $id and summary: $summary")
            PersistenceResult.DELETED
        } else {
            logger.warn("Reservation with id: $id was not deleted because it did not exist in database.")
            PersistenceResult.NO_ACTION
        }
    }

    override suspend fun setCheckIn(
        checkIn: CheckIn,
        id: String,
        startTime: Instant,
        endTime: Instant,
    ): PersistenceResult = suspendTransaction {
        logger.trace("Setting check in for reservation with id: $id")

        val storedReservation: ReservationEntity = currentReservation(id, startTime, endTime)
            ?: return@suspendTransaction PersistenceResult.NO_ACTION

        with(storedReservation) {
            this.checkInTime = checkIn.time.truncatedToMillis()
            this.checkInSourceName = checkIn.sourceName
            this.checkInSourceId = checkIn.sourceId
        }

        persistResultOfDerivedData(storedReservation, "check in")
    }

    override suspend fun setCheckOut(
        checkOut: CheckOut,
        id: String,
        startTime: Instant,
        endTime: Instant,
    ): PersistenceResult = suspendTransaction {
        logger.trace("Setting check out for reservation with id: $id")

        val storedReservation: ReservationEntity = currentReservation(id, startTime, endTime)
            ?: return@suspendTransaction PersistenceResult.NO_ACTION

        with(storedReservation) {
            this.checkOutTime = checkOut.time.truncatedToMillis()
            this.checkOutSourceName = checkOut.sourceName
            this.checkOutSourceId = checkOut.sourceId
        }

        persistResultOfDerivedData(storedReservation, "check out")
    }

    /** The stored reservation, or null if it is gone or its times differ from the snapshot of the caller. */
    private fun currentReservation(id: String, startTime: Instant, endTime: Instant): ReservationEntity? =
        ReservationEntity.findById(id)?.takeIf {
            it.startTime == startTime.truncatedToMillis() && it.endTime == endTime.truncatedToMillis()
        }

    private fun isSnapshotCurrent(id: String, startTime: Instant, endTime: Instant): Boolean =
        currentReservation(id, startTime, endTime) != null

    /** Check-in/out is derived data: it does not bump `version` or set `updated_time`/`updated_by`. */
    private fun persistResultOfDerivedData(storedReservation: ReservationEntity, what: String): PersistenceResult {
        val isDirty: Boolean = storedReservation.writeValues.isNotEmpty()

        return if (isDirty) {
            logger.trace("Updated {} status for reservation with id: {}", what, storedReservation.id)
            PersistenceResult.UPDATED
        } else {
            logger.trace("No changes detected for {} status of reservation with id: {}", what, storedReservation.id)
            PersistenceResult.NO_ACTION
        }
    }

    private fun addReservation(reservation: Reservation, actor: String): PersistenceResult {
        logger.trace("Adding reservation with id: ${reservation.id}")

        val reservationGuests: List<GuestEntity> = reservation.guestIds.mapNotNull { id -> GuestEntity.findById(id) }

        ReservationEntity.new(reservation.id) {
            markCreated(actor)
            summary = reservation.summary
            description = reservation.description
            startTime = reservation.startTime.truncatedToMillis()
            endTime = reservation.endTime.truncatedToMillis()
            guests = SizedCollection(reservationGuests)
            sourceCreatedTime = reservation.sourceCreatedTime?.truncatedToMillis()
            sourceUpdatedTime = reservation.sourceUpdatedTime?.truncatedToMillis()
        }

        logger.trace("Added reservation with id: ${reservation.id} and summary: ${reservation.summary}")
        return PersistenceResult.ADDED
    }

    /**
     * Note that actual database update is only performed if at least one column has changed the value, so
     * calling findByIdAndUpdate is not necessary doing any update if all columns have the same value in stored and new
     * reservation.
     */
    private fun updateReservation(reservation: Reservation, actor: String): PersistenceResult {
        logger.trace("Updating reservation with id: ${reservation.id}")

        val reservationGuests: List<GuestEntity> = reservation.guestIds.mapNotNull { id -> GuestEntity.findById(id) }

        val storedReservation: ReservationEntity =
            ReservationEntity.findById(reservation.id) ?: return PersistenceResult.NO_ACTION

        with(storedReservation) {
            summary = reservation.summary
            description = reservation.description
            startTime = reservation.startTime.truncatedToMillis()
            endTime = reservation.endTime.truncatedToMillis()
            sourceCreatedTime = reservation.sourceCreatedTime?.truncatedToMillis()
            sourceUpdatedTime = reservation.sourceUpdatedTime?.truncatedToMillis()
        }

        // A guest-only edit is a change too, but is not part of writeValues: compare the id sets explicitly
        val guestsChanged: Boolean =
            storedReservation.guests.map { it.id.value }.toSet() != reservationGuests.map { it.id.value }.toSet()
        val isDirty: Boolean = storedReservation.writeValues.isNotEmpty() || guestsChanged

        if (isDirty) {
            storedReservation.markUpdated(actor)
        }

        // This triggers flushing changes and thus empties the writeValues, so we keep it as the last change
        storedReservation.guests = SizedCollection(reservationGuests)

        return if (isDirty) {
            logger.trace("Updated reservation with id: ${reservation.id} and summary: ${reservation.summary}")
            PersistenceResult.UPDATED
        } else {
            logger.trace(
                "No changes detected for reservation with id: ${reservation.id} and summary: ${reservation.summary}",
            )
            PersistenceResult.NO_ACTION
        }
    }

    /**
     * Fetches classification metadata (TO/AT/FROM) for vehicle trips associated with the given reservations.
     *
     * @return A map where the key is the Vehicle Trip ID and the value is the Trip Type string.
     */
    private fun fetchTripTypes(reservationIds: List<EntityID<String>>): Map<String, String> {
        if (reservationIds.isEmpty()) return emptyMap()

        return ReservationVehicleTripTable
            .select(ReservationVehicleTripTable.vehicleTripId, ReservationVehicleTripTable.tripType)
            .where { ReservationVehicleTripTable.reservationId inList reservationIds }
            .associate {
                it[ReservationVehicleTripTable.vehicleTripId].value to it[ReservationVehicleTripTable.tripType]
            }
    }
}
