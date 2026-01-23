package no.slomic.smarthytte.vehicletrips

import io.ktor.util.logging.KtorSimpleLogger
import io.ktor.util.logging.Logger
import no.slomic.smarthytte.common.PersistenceResult
import no.slomic.smarthytte.common.suspendTransaction
import no.slomic.smarthytte.common.truncatedToMillis
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import kotlin.time.Clock

class SqliteVehicleTripRepository : VehicleTripRepository {
    private val logger: Logger = KtorSimpleLogger(SqliteVehicleTripRepository::class.java.name)

    override suspend fun allVehicleTrips(): List<VehicleTrip> = suspendTransaction {
        VehicleTripEntity.all().sortedBy { it.startTime }.map(::daoToModel)
    }

    override suspend fun addOrUpdate(vehicleTrip: VehicleTrip): PersistenceResult = suspendTransaction {
        val entityId: EntityID<String> = EntityID(vehicleTrip.id, VehicleTripTable)
        val storedVehicleTrip: VehicleTripEntity? = VehicleTripEntity.findById(entityId)

        if (storedVehicleTrip == null) {
            addVehicleTrip(vehicleTrip)
        } else {
            updateVehicleTrip(vehicleTrip)
        }
    }

    override suspend fun setNotionId(notionId: String, vehicleTripId: String): PersistenceResult = suspendTransaction {
        logger.trace("Setting notion Id for vehicle trip with id: $vehicleTripId")

        val storedVehicleTrip: VehicleTripEntity =
            VehicleTripEntity.findById(vehicleTripId) ?: return@suspendTransaction PersistenceResult.NO_ACTION

        with(storedVehicleTrip) {
            this.notionId = notionId
            version = storedVehicleTrip.version.inc()
            updatedTime = Clock.System.now().truncatedToMillis()
        }

        logger.trace("Notion id set for vehicle trip with id: $vehicleTripId")
        PersistenceResult.UPDATED
    }

    @Suppress("DuplicatedCode")
    private fun addVehicleTrip(vehicleTrip: VehicleTrip): PersistenceResult {
        logger.trace("Adding vehicle trip with id: ${vehicleTrip.id}")

        VehicleTripEntity.new(vehicleTrip.id) {
            averageEnergyConsumption = vehicleTrip.averageEnergyConsumption
            averageEnergyConsumptionUnit = vehicleTrip.averageEnergyConsumptionUnit
            averageSpeed = vehicleTrip.averageSpeed
            createdTime = Clock.System.now().truncatedToMillis()
            distance = vehicleTrip.distance
            distanceUnit = vehicleTrip.distanceUnit
            duration = vehicleTrip.duration
            durationUnit = vehicleTrip.durationUnit
            endAddress = vehicleTrip.endAddress
            endCity = vehicleTrip.endCity
            endTime = vehicleTrip.endTime.truncatedToMillis()
            energyRegenerated = vehicleTrip.energyRegenerated
            energyRegeneratedUnit = vehicleTrip.energyRegeneratedUnit
            speedUnit = vehicleTrip.speedUnit
            startAddress = vehicleTrip.startAddress
            startCity = vehicleTrip.startCity
            startTime = vehicleTrip.startTime.truncatedToMillis()
            totalDistance = vehicleTrip.totalDistance
        }

        logger.trace("Added vehicle trip with id: ${vehicleTrip.id}")
        return PersistenceResult.ADDED
    }

    /**
     * Note that actual database update is only performed if at least one column has changed the value, so
     * calling findByIdAndUpdate is not necessary doing any update if all columns have the same value in stored and new
     * guest.
     */
    @Suppress("DuplicatedCode")
    private fun updateVehicleTrip(vehicleTrip: VehicleTrip): PersistenceResult {
        logger.trace("Updating vehicle trip with id: ${vehicleTrip.id}")

        val updatedVehicleTrip: VehicleTripEntity =
            VehicleTripEntity.findById(vehicleTrip.id) ?: return PersistenceResult.NO_ACTION

        with(updatedVehicleTrip) {
            averageEnergyConsumption = vehicleTrip.averageEnergyConsumption
            averageEnergyConsumptionUnit = vehicleTrip.averageEnergyConsumptionUnit
            averageSpeed = vehicleTrip.averageSpeed
            distance = vehicleTrip.distance
            distanceUnit = vehicleTrip.distanceUnit
            duration = vehicleTrip.duration
            durationUnit = vehicleTrip.durationUnit
            endAddress = vehicleTrip.endAddress
            endCity = vehicleTrip.endCity
            endTime = vehicleTrip.endTime.truncatedToMillis()
            energyRegenerated = vehicleTrip.energyRegenerated
            energyRegeneratedUnit = vehicleTrip.energyRegeneratedUnit
            speedUnit = vehicleTrip.speedUnit
            startAddress = vehicleTrip.startAddress
            startCity = vehicleTrip.startCity
            startTime = vehicleTrip.startTime.truncatedToMillis()
            totalDistance = vehicleTrip.totalDistance
        }

        val isDirty: Boolean = updatedVehicleTrip.writeValues.isNotEmpty()

        return if (isDirty) {
            updatedVehicleTrip.version = updatedVehicleTrip.version.inc()
            updatedVehicleTrip.updatedTime = Clock.System.now().truncatedToMillis()

            logger.trace("Updated vehicle trip with id: ${vehicleTrip.id}")
            PersistenceResult.UPDATED
        } else {
            logger.trace("No changes detected for vehicle trip with id: ${vehicleTrip.id}")
            PersistenceResult.NO_ACTION
        }
    }
}
