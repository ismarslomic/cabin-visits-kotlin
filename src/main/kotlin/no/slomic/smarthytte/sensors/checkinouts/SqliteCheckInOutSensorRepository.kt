package no.slomic.smarthytte.sensors.checkinouts

import io.ktor.util.logging.KtorSimpleLogger
import io.ktor.util.logging.Logger
import kotlinx.datetime.Clock
import no.slomic.smarthytte.common.PersistenceResult
import no.slomic.smarthytte.common.suspendTransaction
import no.slomic.smarthytte.common.truncatedToMillis
import org.jetbrains.exposed.dao.id.EntityID

class SqliteCheckInOutSensorRepository : CheckInOutSensorRepository {
    private val logger: Logger = KtorSimpleLogger(SqliteCheckInOutSensorRepository::class.java.name)

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

    private fun addCheckInOutSensor(checkInOutSensor: CheckInOutSensor): PersistenceResult {
        logger.trace("Adding check in/out with id: ${checkInOutSensor.id}")

        CheckInOutSensorEntity.new(checkInOutSensor.id) {
            time = checkInOutSensor.time.truncatedToMillis()
            status = checkInOutSensor.status
            createdTime = Clock.System.now().truncatedToMillis()
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
            time = checkInOutSensor.time.truncatedToMillis()
            status = checkInOutSensor.status
        }

        val isDirty: Boolean = updatedCheckInOut.writeValues.isNotEmpty()

        return if (isDirty) {
            updatedCheckInOut.version = updatedCheckInOut.version.inc()
            updatedCheckInOut.updatedTime = Clock.System.now().truncatedToMillis()

            logger.trace("Updated check in/out with id: ${checkInOutSensor.id}")
            PersistenceResult.UPDATED
        } else {
            logger.trace("No changes detected for check in/out with id: ${checkInOutSensor.id}")
            PersistenceResult.NO_ACTION
        }
    }
}
