package no.slomic.smarthytte.sensors.checkinouts

import io.ktor.util.logging.KtorSimpleLogger
import io.ktor.util.logging.Logger
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import no.slomic.smarthytte.common.PersistenceResult
import no.slomic.smarthytte.common.suspendTransaction
import org.jetbrains.exposed.dao.id.EntityID

class SqliteCheckInOutSensorRepository : CheckInOutSensorRepository {
    private val logger: Logger = KtorSimpleLogger(SqliteCheckInOutSensorRepository::class.java.name)
    private val checkInSyncId: Int = 1

    override suspend fun allCheckInOuts(): List<CheckInOutSensor> = suspendTransaction {
        CheckInOutSensorEntity.all().sortedBy { it.time }.map(::daoToModel)
    }

    override suspend fun addOrUpdate(checkInOutSensor: CheckInOutSensor): PersistenceResult = suspendTransaction {
        val entityId: EntityID<String> = EntityID(checkInOutSensor.id, CheckInOutSensorTable)
        val storedCheckInOut: CheckInOutSensorEntity? = CheckInOutSensorEntity.findById(entityId)

        if (storedCheckInOut == null) {
            addCheckInOutSensor(checkInOutSensor)
        } else {
            updateCheckInOutSensor(checkInOutSensor)
        }
    }

    override suspend fun latestTime(): Instant? =
        suspendTransaction { CheckInOutSensorSyncEntity.findById(checkInSyncId)?.latestTime }

    override suspend fun addOrUpdate(latestTime: Instant) {
        suspendTransaction {
            val storedCheckInOutSync: CheckInOutSensorSyncEntity? = CheckInOutSensorSyncEntity.findById(checkInSyncId)

            var isUpdated = false
            if (storedCheckInOutSync == null) {
                CheckInOutSensorSyncEntity.new(checkInSyncId) {
                    this.latestTime = latestTime
                    updatedTime = Clock.System.now()
                    isUpdated = true
                }
            } else {
                storedCheckInOutSync.latestTime = latestTime

                val isDirty: Boolean = storedCheckInOutSync.writeValues.isNotEmpty()

                if (isDirty) {
                    storedCheckInOutSync.updatedTime = Clock.System.now()
                    isUpdated = true
                }
            }

            if (isUpdated) {
                logger.trace("Latest check in/out sync time updated.")
            } else {
                logger.trace("No need to update the latest check in/out sync time.")
            }
        }
    }

    private fun addCheckInOutSensor(checkInOutSensor: CheckInOutSensor): PersistenceResult {
        logger.trace("Adding check in/out with id: ${checkInOutSensor.id}")

        CheckInOutSensorEntity.new(checkInOutSensor.id) {
            time = checkInOutSensor.time
            status = checkInOutSensor.status
            createdTime = Clock.System.now()
        }

        logger.trace("Added check in with id: ${checkInOutSensor.id}")
        return PersistenceResult.ADDED
    }

    /**
     * Note that actual database update is only performed if at least one column has changed the value, so
     * calling findByIdAndUpdate is not necessary doing any update if all columns have the same value in stored and new
     * check in.
     */
    private fun updateCheckInOutSensor(checkInOutSensor: CheckInOutSensor): PersistenceResult {
        logger.trace("Updating check in/out with id: ${checkInOutSensor.id}")

        val updatedCheckInOut: CheckInOutSensorEntity =
            CheckInOutSensorEntity.findById(checkInOutSensor.id) ?: return PersistenceResult.NO_ACTION

        with(updatedCheckInOut) {
            time = checkInOutSensor.time
            status = checkInOutSensor.status
        }

        val isDirty: Boolean = updatedCheckInOut.writeValues.isNotEmpty()

        return if (isDirty) {
            updatedCheckInOut.version = updatedCheckInOut.version.inc()
            updatedCheckInOut.updatedTime = Clock.System.now()

            logger.trace("Updated check in/out with id: ${checkInOutSensor.id}")
            PersistenceResult.UPDATED
        } else {
            logger.trace("No changes detected for check in/out with id: ${checkInOutSensor.id}")
            PersistenceResult.NO_ACTION
        }
    }
}
