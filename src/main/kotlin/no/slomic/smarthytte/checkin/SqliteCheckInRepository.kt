package no.slomic.smarthytte.checkin

import io.ktor.util.logging.KtorSimpleLogger
import io.ktor.util.logging.Logger
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import no.slomic.smarthytte.common.suspendTransaction
import org.jetbrains.exposed.dao.id.EntityID

class SqliteCheckInRepository : CheckInRepository {
    private val logger: Logger = KtorSimpleLogger(SqliteCheckInRepository::class.java.name)
    private val checkInSyncId: Int = 1

    override suspend fun allCheckIns(): List<CheckIn> = suspendTransaction {
        CheckInEntity.all().sortedBy { it.timestamp }.map(::daoToModel)
    }

    override suspend fun addOrUpdate(checkIn: CheckIn): CheckIn = suspendTransaction {
        val entityId: EntityID<String> = EntityID(checkIn.id, CheckInTable)
        val storedCheckIn: CheckInEntity? = CheckInEntity.findById(entityId)

        if (storedCheckIn == null) {
            addEvent(checkIn)
        } else {
            updateEvent(checkIn)!!
        }
    }

    private fun addEvent(checkIn: CheckIn): CheckIn {
        logger.info("Adding check in with id: ${checkIn.id}")

        val newEvent = CheckInEntity.new(checkIn.id) {
            timestamp = checkIn.timestamp
            status = checkIn.status
            created = Clock.System.now()
        }

        logger.info("Added check in with id: ${checkIn.id}")

        return daoToModel(newEvent)
    }

    /**
     * Note that actual database update is only performed if at least one column has changed the value, so
     * calling findByIdAndUpdate is not necessary doing any update if all columns have the same value in stored and new
     * check in.
     */
    private fun updateEvent(checkIn: CheckIn): CheckIn? {
        logger.info("Updating check in with id: ${checkIn.id}")

        val updatedCheckIn: CheckInEntity = CheckInEntity.findById(checkIn.id) ?: return null

        with(updatedCheckIn) {
            timestamp = checkIn.timestamp
            status = checkIn.status
        }

        val isDirty: Boolean = updatedCheckIn.writeValues.isNotEmpty()

        if (isDirty) {
            updatedCheckIn.version = updatedCheckIn.version.inc()
            updatedCheckIn.updated = Clock.System.now()
        }

        if (isDirty) {
            logger.info("Updated check in with id: ${checkIn.id}")
        } else {
            logger.info("No changes detected for check in with id: ${checkIn.id}")
        }

        return daoToModel(updatedCheckIn)
    }

    override suspend fun lastCheckInTimestamp(): Instant? =
        suspendTransaction { CheckInSyncEntity.findById(checkInSyncId)?.latestTimestamp }

    override suspend fun addOrUpdate(latestCheckInTimestamp: Instant) {
        suspendTransaction {
            val storedCheckInSync: CheckInSyncEntity? = CheckInSyncEntity.findById(checkInSyncId)

            var isUpdated = false
            if (storedCheckInSync == null) {
                CheckInSyncEntity.new(checkInSyncId) {
                    latestTimestamp = latestCheckInTimestamp
                    updated = Clock.System.now()
                    isUpdated = true
                }
            } else {
                storedCheckInSync.latestTimestamp = latestCheckInTimestamp

                val isDirty: Boolean = storedCheckInSync.writeValues.isNotEmpty()

                if (isDirty) {
                    storedCheckInSync.updated = Clock.System.now()
                    isUpdated = true
                }
            }

            if (isUpdated) {
                logger.info("Latest check in sync timestamp updated.")
            } else {
                logger.info("No need to update the check in sync timestamp.")
            }
        }
    }
}
