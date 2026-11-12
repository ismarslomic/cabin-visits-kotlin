package no.slomic.smarthytte.reservations

import io.ktor.util.logging.KtorSimpleLogger
import io.ktor.util.logging.Logger
import kotlinx.datetime.LocalDate
import kotlinx.datetime.atStartOfDayIn
import no.slomic.smarthytte.calendar.enqueueCalendarEventCreated
import no.slomic.smarthytte.calendar.enqueueCalendarEventDeleted
import no.slomic.smarthytte.calendar.enqueueCalendarEventMoved
import no.slomic.smarthytte.checkinouts.CheckIn
import no.slomic.smarthytte.checkinouts.CheckOut
import no.slomic.smarthytte.common.PersistenceResult
import no.slomic.smarthytte.common.markCreated
import no.slomic.smarthytte.common.markUpdated
import no.slomic.smarthytte.common.suspendReadTransaction
import no.slomic.smarthytte.common.suspendTransaction
import no.slomic.smarthytte.common.truncatedToMillis
import no.slomic.smarthytte.common.utcTimeZone
import no.slomic.smarthytte.guests.GuestEntity
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.Transaction
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.inSubQuery
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.dao.flushCache
import org.jetbrains.exposed.v1.dao.with
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import java.util.UUID
import kotlin.time.Instant

@Suppress("TooManyFunctions")
class SqliteReservationRepository : ReservationRepository {
    private val logger: Logger = KtorSimpleLogger(SqliteReservationRepository::class.java.name)

    override suspend fun allReservations(): List<Reservation> = suspendReadTransaction {
        val reservationEntities = ReservationEntity.all()
            .with(ReservationEntity::vehicleTrips)
            .sortedBy { it.startTime }

        toModels(reservationEntities)
    }

    override suspend fun reservations(guestId: String?, from: LocalDate?, toExclusive: LocalDate?): List<Reservation> =
        suspendReadTransaction {
            var condition: Op<Boolean> = Op.TRUE
            guestId?.let { id ->
                val linked = ReservationGuestTable.select(ReservationGuestTable.reservation)
                    .where { ReservationGuestTable.guest eq id }
                condition = condition and (ReservationTable.id inSubQuery linked)
            }
            // UTC dates: startDate < toExclusive is startTime < the start of that day, and endDate >= from is
            // endTime >= the start of from
            toExclusive?.let {
                condition =
                    condition and (ReservationTable.startTime less it.atStartOfDayIn(utcTimeZone))
            }
            from?.let { condition = condition and (ReservationTable.endTime greaterEq it.atStartOfDayIn(utcTimeZone)) }

            val reservationEntities = ReservationEntity.find(condition)
                .with(ReservationEntity::vehicleTrips)
                .sortedWith(compareBy({ it.startTime }, { it.id.value }))

            toModels(reservationEntities)
        }

    override suspend fun reservationById(id: String): Reservation? = suspendReadTransaction {
        val entityId = EntityID(id, ReservationTable)
        val storedReservation = ReservationEntity.findById(entityId)

        storedReservation?.let { toModels(listOf(it)).single() }
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

    override suspend fun createReservation(
        startTime: Instant,
        endTime: Instant,
        guests: List<ReservationGuest>,
        actor: String,
    ): ReservationCreateOutcome = suspendTransaction {
        val start = startTime.truncatedToMillis()
        val end = endTime.truncatedToMillis()
        val loaded = loadGuests(guests)

        when {
            loaded.missing.isNotEmpty() -> ReservationCreateOutcome.UnknownGuests(loaded.missing)

            existsOverlapping(start, end, excludeId = null) -> ReservationCreateOutcome.Overlap

            else -> {
                // Generated inside the transaction, which is re-run when SQLite is busy
                val id = UUID.randomUUID().toString().replace("-", "")
                logger.trace("Creating reservation with id: $id from the API")

                val created = ReservationEntity.new(id) {
                    markCreated(actor)
                    this.startTime = start
                    this.endTime = end
                }
                replaceGuests(id, loaded.found)
                enqueueCalendarEventCreated(id)

                ReservationCreateOutcome.Created(toModels(listOf(created)).single())
            }
        }
    }

    override suspend fun updateReservation(
        id: String,
        expectedVersion: Short,
        startTime: Instant,
        endTime: Instant,
        guests: List<ReservationGuest>,
        actor: String,
    ): ReservationUpdateOutcome = suspendTransaction {
        val start = startTime.truncatedToMillis()
        val end = endTime.truncatedToMillis()
        val stored: ReservationEntity? = ReservationEntity.findById(id)
        val loaded = loadGuests(guests)

        when {
            stored == null -> ReservationUpdateOutcome.NotFound

            stored.version != expectedVersion -> ReservationUpdateOutcome.VersionMismatch

            loaded.missing.isNotEmpty() -> ReservationUpdateOutcome.UnknownGuests(loaded.missing)

            else -> {
                val timesChanged = stored.startTime != start || stored.endTime != end
                val storedGuests = fetchGuests(listOf(stored.id))[stored.id.value].orEmpty()
                val guestsChanged = storedGuests != loaded.found

                when {
                    !timesChanged && !guestsChanged -> {
                        logger.trace("No changes detected for reservation with id: $id")
                        ReservationUpdateOutcome.Unchanged(toModels(listOf(stored)).single())
                    }

                    timesChanged && existsOverlapping(start, end, excludeId = id) -> ReservationUpdateOutcome.Overlap

                    else -> {
                        applyUpdate(stored, start, end, loaded.found, timesChanged, actor)
                        ReservationUpdateOutcome.Updated(toModels(listOf(stored)).single(), timesChanged)
                    }
                }
            }
        }
    }

    override suspend fun deleteReservation(
        id: String,
        expectedVersion: Short,
        actor: String,
    ): ReservationDeleteOutcome = suspendTransaction {
        val stored: ReservationEntity? = ReservationEntity.findById(id)

        when {
            stored == null -> {
                logger.warn("Reservation with id: $id was not deleted because it did not exist in database.")
                ReservationDeleteOutcome.NotFound
            }

            stored.version != expectedVersion -> ReservationDeleteOutcome.VersionMismatch

            else -> {
                // What the log line needs must be read before the delete
                val guestIds = fetchGuests(listOf(stored.id))[stored.id.value].orEmpty().map { it.guestId }
                val (start, end) = stored.startTime to stored.endTime

                stored.delete()
                enqueueCalendarEventDeleted(id)

                logger.info(
                    "Reservation deleted: id={}, startTime={}, endTime={}, guestIds={}, actor={}",
                    id,
                    start,
                    end,
                    guestIds,
                    actor,
                )
                ReservationDeleteOutcome.Deleted
            }
        }
    }

    /**
     * Applies a real change. A time change also clears the derived data (trip links and check-in/out) in the same
     * transaction, which neither bumps nor sets anything by itself: the reservation row gets the one
     * `markUpdated`. A guest-only change leaves the derived data alone and queues no calendar operation, because the
     * event has a fixed title and no guests.
     */
    private fun Transaction.applyUpdate(
        stored: ReservationEntity,
        start: Instant,
        end: Instant,
        guests: List<ReservationGuest>,
        timesChanged: Boolean,
        actor: String,
    ) {
        if (timesChanged) {
            stored.startTime = start
            stored.endTime = end
            stored.clearCheckInOut()
            ReservationVehicleTripTable.deleteWhere { reservationId eq stored.id }
            enqueueCalendarEventMoved(stored.id.value)
        }

        stored.markUpdated(actor)
        replaceGuests(stored.id.value, guests)
    }

    private fun ReservationEntity.clearCheckInOut() {
        checkInTime = null
        checkInSourceName = null
        checkInSourceId = null
        checkOutTime = null
        checkOutSourceName = null
        checkOutSourceId = null
    }

    /** Another reservation overlaps `[start, end)` when it starts before [end] and ends after [start]. */
    private fun existsOverlapping(start: Instant, end: Instant, excludeId: String?): Boolean {
        val overlaps = (ReservationTable.startTime less end) and (ReservationTable.endTime greater start)
        val condition = excludeId?.let { overlaps and (ReservationTable.id neq it) } ?: overlaps

        return !ReservationEntity.find(condition).empty()
    }

    /** [found] is sorted by guest id, like the stored guests, so the two lists compare equal when nothing changed. */
    private class LoadedGuests(val found: List<ReservationGuest>, val missing: List<String>)

    private fun loadGuests(guests: List<ReservationGuest>): LoadedGuests {
        val distinct = guests.distinctBy { it.guestId }
        val existing = distinct.mapNotNull { GuestEntity.findById(it.guestId)?.id?.value }.toSet()

        return LoadedGuests(
            found = distinct.filter { it.guestId in existing }.sortedBy { it.guestId },
            missing = distinct.map { it.guestId }.filter { it !in existing },
        )
    }

    /** Maps the entities with their guests (and stays) and vehicle trip types, read with two queries in total. */
    private fun toModels(entities: List<ReservationEntity>): List<Reservation> {
        val ids = entities.map { it.id }
        val guests = fetchGuests(ids)
        val tripTypes = fetchTripTypes(ids)

        return entities.map { daoToModel(it, guests[it.id.value].orEmpty(), tripTypes) }
    }

    /** The guests of each reservation (by reservation id), sorted by guest id. */
    private fun fetchGuests(reservationIds: List<EntityID<String>>): Map<String, List<ReservationGuest>> {
        if (reservationIds.isEmpty()) return emptyMap()

        return ReservationGuestTable.selectAll()
            .where { ReservationGuestTable.reservation inList reservationIds }
            .groupBy(
                keySelector = { it[ReservationGuestTable.reservation].value },
                valueTransform = {
                    ReservationGuest(
                        guestId = it[ReservationGuestTable.guest].value,
                        fromDate = it[ReservationGuestTable.fromDate],
                        toDate = it[ReservationGuestTable.toDate],
                    )
                },
            )
            .mapValues { (_, guests) -> guests.sortedBy { it.guestId } }
    }

    /** Replaces the guest links of the reservation, with the stays. The row of the reservation must exist. */
    private fun Transaction.replaceGuests(reservationId: String, guests: List<ReservationGuest>) {
        // The reservation row can still be waiting in the entity cache
        flushCache()
        ReservationGuestTable.deleteWhere { reservation eq reservationId }
        ReservationGuestTable.batchInsert(guests) { guest ->
            this[ReservationGuestTable.reservation] = reservationId
            this[ReservationGuestTable.guest] = guest.guestId
            this[ReservationGuestTable.fromDate] = guest.fromDate
            this[ReservationGuestTable.toDate] = guest.toDate
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

    private fun Transaction.addReservation(reservation: Reservation, actor: String): PersistenceResult {
        logger.trace("Adding reservation with id: ${reservation.id}")

        val reservationGuests = loadGuests(reservation.guests).found

        ReservationEntity.new(reservation.id) {
            markCreated(actor)
            summary = reservation.summary
            description = reservation.description
            startTime = reservation.startTime.truncatedToMillis()
            endTime = reservation.endTime.truncatedToMillis()
            sourceCreatedTime = reservation.sourceCreatedTime?.truncatedToMillis()
            sourceUpdatedTime = reservation.sourceUpdatedTime?.truncatedToMillis()
        }

        replaceGuests(reservation.id, reservationGuests)

        logger.trace("Added reservation with id: ${reservation.id} and summary: ${reservation.summary}")
        return PersistenceResult.ADDED
    }

    /**
     * Note that actual database update is only performed if at least one column has changed the value, so
     * calling findByIdAndUpdate is not necessary doing any update if all columns have the same value in stored and new
     * reservation.
     */
    private fun Transaction.updateReservation(reservation: Reservation, actor: String): PersistenceResult {
        logger.trace("Updating reservation with id: ${reservation.id}")

        val reservationGuests = loadGuests(reservation.guests).found

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

        // A guest-only edit is a change too, but is not part of writeValues: compare the guests explicitly
        val guestsChanged: Boolean =
            fetchGuests(listOf(storedReservation.id))[storedReservation.id.value].orEmpty() != reservationGuests
        val isDirty: Boolean = storedReservation.writeValues.isNotEmpty() || guestsChanged

        if (isDirty) {
            storedReservation.markUpdated(actor)
        }

        replaceGuests(reservation.id, reservationGuests)

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
