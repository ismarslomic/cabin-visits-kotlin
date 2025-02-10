package no.slomic.smarthytte.reservations

import io.ktor.util.logging.KtorSimpleLogger
import io.ktor.util.logging.Logger
import kotlinx.datetime.Clock
import no.slomic.smarthytte.checkinouts.CheckIn
import no.slomic.smarthytte.checkinouts.CheckOut
import no.slomic.smarthytte.common.UpsertStatus
import no.slomic.smarthytte.common.suspendTransaction
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

    override suspend fun setNotionId(notionId: String, id: String): UpsertStatus = suspendTransaction {
        logger.info("Setting notion Id for reservation with id: $id")

        val storedReservation: ReservationEntity =
            ReservationEntity.findById(id) ?: return@suspendTransaction UpsertStatus.NO_ACTION

        with(storedReservation) {
            this.notionId = notionId
            version = storedReservation.version.inc()
            updatedTime = Clock.System.now()
        }

        logger.info("Notion id set for reservation with id: $id")

        UpsertStatus.UPDATED
    }

    @Suppress("DuplicatedCode")
    override suspend fun setCheckIn(checkIn: CheckIn, id: String): UpsertStatus = suspendTransaction {
        logger.info("Setting check in for reservation with id: $id")

        val storedReservation: ReservationEntity =
            ReservationEntity.findById(id) ?: return@suspendTransaction UpsertStatus.NO_ACTION

        with(storedReservation) {
            this.checkInTime = checkIn.time
            this.checkInSourceName = checkIn.sourceName
            this.checkInSourceId = checkIn.sourceId
            version = storedReservation.version.inc()
            updatedTime = Clock.System.now()
        }

        logger.info("Check in set for reservation with id: $id")
        UpsertStatus.UPDATED
    }

    @Suppress("DuplicatedCode")
    override suspend fun setCheckOut(checkOut: CheckOut, id: String): UpsertStatus = suspendTransaction {
        logger.info("Setting check out for reservation with id: $id")

        val storedReservation: ReservationEntity =
            ReservationEntity.findById(id) ?: return@suspendTransaction UpsertStatus.NO_ACTION

        with(storedReservation) {
            this.checkOutTime = checkOut.time
            this.checkOutSourceName = checkOut.sourceName
            this.checkOutSourceId = checkOut.sourceId
            version = storedReservation.version.inc()
            updatedTime = Clock.System.now()
        }

        logger.info("Check out set for reservation with id: $id")
        UpsertStatus.UPDATED
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
