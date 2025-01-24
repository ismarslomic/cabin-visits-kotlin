package no.slomic.smarthytte.reservations

import io.ktor.util.logging.KtorSimpleLogger
import io.ktor.util.logging.Logger
import kotlinx.datetime.Clock
import no.slomic.smarthytte.common.suspendTransaction
import no.slomic.smarthytte.guest.GuestEntity
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

    override suspend fun addOrUpdate(reservation: Reservation): Reservation = suspendTransaction {
        val entityId: EntityID<String> = EntityID(reservation.id, ReservationTable)
        val storedReservation: ReservationEntity? = ReservationEntity.findById(entityId)

        if (storedReservation == null) {
            addReservation(reservation)
        } else {
            updateReservation(reservation)!!
        }
    }

    override suspend fun deleteReservation(id: String): Boolean = suspendTransaction {
        logger.info("Deleting reservation with id: $id")
        val entityId: EntityID<String> = EntityID(id, ReservationTable)
        val storedReservation: ReservationEntity? = ReservationEntity.findById(entityId)

        storedReservation?.delete()

        val wasDeleted: Boolean = storedReservation != null
        val summary: String? = storedReservation?.summary

        logger.info("Deleted reservation with id: $id and summary: $summary was successful: $wasDeleted")

        wasDeleted
    }

    private fun addReservation(reservation: Reservation): Reservation {
        logger.info("Adding reservation with id: ${reservation.id}")

        val eventGuests: List<GuestEntity> = reservation.guestIds.mapNotNull { id -> GuestEntity.findById(id) }

        val newEvent = ReservationEntity.new(reservation.id) {
            summary = reservation.summary
            description = reservation.description
            startTime = reservation.startTime
            endTime = reservation.endTime
            guests = SizedCollection(eventGuests)
            sourceCreatedTime = reservation.sourceCreatedTime
            sourceUpdatedTime = reservation.sourceUpdatedTime
            createdTime = Clock.System.now()
        }

        logger.info("Added reservation with id: ${reservation.id} and summary: ${reservation.summary}")

        return daoToModel(newEvent)
    }

    /**
     * Note that actual database update is only performed if at least one column has changed the value, so
     * calling findByIdAndUpdate is not necessary doing any update if all columns have the same value in stored and new
     * event.
     */
    private fun updateReservation(reservation: Reservation): Reservation? {
        logger.info("Updating reservation with id: ${reservation.id}")

        val reservationGuests: List<GuestEntity> = reservation.guestIds.mapNotNull { id -> GuestEntity.findById(id) }

        val storedReservation: ReservationEntity = ReservationEntity.findById(reservation.id) ?: return null

        with(storedReservation) {
            summary = reservation.summary
            description = reservation.description
            startTime = reservation.startTime
            endTime = reservation.endTime
            sourceCreatedTime = reservation.sourceCreatedTime
            sourceUpdatedTime = reservation.sourceUpdatedTime
        }

        val isDirty: Boolean = storedReservation.writeValues.isNotEmpty()

        if (isDirty) {
            storedReservation.version = storedReservation.version.inc()
            storedReservation.updatedTime = Clock.System.now()
        }

        // This triggers flushing changes and thus empties the writeValues, so we keep it as the last change
        storedReservation.guests = SizedCollection(reservationGuests)

        if (isDirty) {
            logger.info("Updated reservation with id: ${reservation.id} and summary: ${reservation.summary}")
        } else {
            logger.info(
                "No changes detected for reservation with id: ${reservation.id} and summary: ${reservation.summary}",
            )
        }

        return daoToModel(storedReservation)
    }
}
