package no.slomic.smarthytte.reservations

import io.ktor.util.logging.KtorSimpleLogger
import io.ktor.util.logging.Logger
import kotlinx.datetime.Clock
import no.slomic.smarthytte.checkinouts.CheckIn
import no.slomic.smarthytte.checkinouts.CheckOut
import no.slomic.smarthytte.common.PersistenceResult
import no.slomic.smarthytte.common.suspendTransaction
import no.slomic.smarthytte.common.truncatedToMillis
import no.slomic.smarthytte.guests.GuestEntity
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.SizedCollection

class SqliteReservationRepository : ReservationRepository {
    private val logger: Logger = KtorSimpleLogger(SqliteReservationRepository::class.java.name)

    override suspend fun allReservations(): List<Reservation> = suspendTransaction {
        ReservationEntity.all().sortedBy { it.startTime }.map(::daoToModel)
    }

    override suspend fun reservationById(id: String): Reservation? = suspendTransaction {
        val entityId: EntityID<String> = EntityID(id, ReservationTable)
        val storedReservation: ReservationEntity? = ReservationEntity.findById(entityId)

        storedReservation?.let { daoToModel(it) }
    }

    override suspend fun addOrUpdate(reservation: Reservation): PersistenceResult = suspendTransaction {
        val entityId: EntityID<String> = EntityID(reservation.id, ReservationTable)
        val storedReservation: ReservationEntity? = ReservationEntity.findById(entityId)

        if (storedReservation == null) {
            addReservation(reservation)
        } else {
            updateReservation(reservation)
        }
    }

    override suspend fun deleteReservation(id: String): PersistenceResult = suspendTransaction {
        logger.trace("Deleting reservation with id: $id")
        val entityId: EntityID<String> = EntityID(id, ReservationTable)
        val storedReservation: ReservationEntity? = ReservationEntity.findById(entityId)

        storedReservation?.delete()

        val wasDeleted: Boolean = storedReservation != null
        val summary: String? = storedReservation?.summary

        return@suspendTransaction if (wasDeleted) {
            logger.trace("Deleted reservation with id: $id and summary: $summary")
            PersistenceResult.DELETED
        } else {
            logger.warn("Reservation with id: $id was not deleted because it did not exist in database.")
            PersistenceResult.NO_ACTION
        }
    }

    override suspend fun setNotionId(notionId: String, id: String): PersistenceResult = suspendTransaction {
        logger.trace("Setting notion Id for reservation with id: $id")

        val storedReservation: ReservationEntity =
            ReservationEntity.findById(id) ?: return@suspendTransaction PersistenceResult.NO_ACTION

        with(storedReservation) {
            this.notionId = notionId
            version = storedReservation.version.inc()
            updatedTime = Clock.System.now().truncatedToMillis()
        }

        logger.trace("Notion id set for reservation with id: $id")
        PersistenceResult.UPDATED
    }

    @Suppress("DuplicatedCode")
    override suspend fun setCheckIn(checkIn: CheckIn, id: String): PersistenceResult = suspendTransaction {
        logger.trace("Setting check in for reservation with id: $id")

        val storedReservation: ReservationEntity =
            ReservationEntity.findById(id) ?: return@suspendTransaction PersistenceResult.NO_ACTION

        with(storedReservation) {
            this.checkInTime = checkIn.time.truncatedToMillis()
            this.checkInSourceName = checkIn.sourceName
            this.checkInSourceId = checkIn.sourceId
        }

        val isDirty: Boolean = storedReservation.writeValues.isNotEmpty()

        if (isDirty) {
            storedReservation.version = storedReservation.version.inc()
            storedReservation.updatedTime = Clock.System.now().truncatedToMillis()

            logger.trace(
                "Updated check in status for reservation with id: {} and summary: {}",
                storedReservation.id,
                storedReservation.summary,
            )
            PersistenceResult.UPDATED
        } else {
            logger.trace(
                "No changes detected for check in status of reservation with id: {} and summary: {}",
                storedReservation.id,
                storedReservation.summary,
            )
            PersistenceResult.NO_ACTION
        }
    }

    @Suppress("DuplicatedCode")
    override suspend fun setCheckOut(checkOut: CheckOut, id: String): PersistenceResult = suspendTransaction {
        logger.trace("Setting check out for reservation with id: $id")

        val storedReservation: ReservationEntity =
            ReservationEntity.findById(id) ?: return@suspendTransaction PersistenceResult.NO_ACTION

        with(storedReservation) {
            this.checkOutTime = checkOut.time.truncatedToMillis()
            this.checkOutSourceName = checkOut.sourceName
            this.checkOutSourceId = checkOut.sourceId
        }

        val isDirty: Boolean = storedReservation.writeValues.isNotEmpty()

        if (isDirty) {
            storedReservation.version = storedReservation.version.inc()
            storedReservation.updatedTime = Clock.System.now().truncatedToMillis()

            logger.trace(
                "Updated check out status for reservation with id: {} and summary: {}",
                storedReservation.id,
                storedReservation.summary,
            )
            PersistenceResult.UPDATED
        } else {
            logger.trace(
                "No changes detected for check out status of reservation with id: {} and summary: {}",
                storedReservation.id,
                storedReservation.summary,
            )
            PersistenceResult.NO_ACTION
        }
    }

    private fun addReservation(reservation: Reservation): PersistenceResult {
        logger.trace("Adding reservation with id: ${reservation.id}")

        val reservationGuests: List<GuestEntity> = reservation.guestIds.mapNotNull { id -> GuestEntity.findById(id) }

        ReservationEntity.new(reservation.id) {
            summary = reservation.summary
            description = reservation.description
            startTime = reservation.startTime.truncatedToMillis()
            endTime = reservation.endTime.truncatedToMillis()
            guests = SizedCollection(reservationGuests)
            sourceCreatedTime = reservation.sourceCreatedTime?.truncatedToMillis()
            sourceUpdatedTime = reservation.sourceUpdatedTime?.truncatedToMillis()
            createdTime = Clock.System.now().truncatedToMillis()
        }

        logger.trace("Added reservation with id: ${reservation.id} and summary: ${reservation.summary}")
        return PersistenceResult.ADDED
    }

    /**
     * Note that actual database update is only performed if at least one column has changed the value, so
     * calling findByIdAndUpdate is not necessary doing any update if all columns have the same value in stored and new
     * reservation.
     */
    private fun updateReservation(reservation: Reservation): PersistenceResult {
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

        val isDirty: Boolean = storedReservation.writeValues.isNotEmpty()

        if (isDirty) {
            storedReservation.version = storedReservation.version.inc()
            storedReservation.updatedTime = Clock.System.now().truncatedToMillis()
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
}
