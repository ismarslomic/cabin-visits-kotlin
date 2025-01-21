package no.slomic.smarthytte.vehicletrip

import io.ktor.util.logging.KtorSimpleLogger
import io.ktor.util.logging.Logger
import kotlinx.datetime.Clock
import no.slomic.smarthytte.common.UpsertStatus
import no.slomic.smarthytte.common.suspendTransaction
import org.jetbrains.exposed.dao.id.EntityID

class SqliteVehicleTripRepository : VehicleTripRepository {
    private val logger: Logger = KtorSimpleLogger(SqliteVehicleTripRepository::class.java.name)

    override suspend fun allVehicleTrips(): List<VehicleTrip> = suspendTransaction {
        VehicleTripEntity.all().sortedBy { it.startTimestamp }.map(::daoToModel)
    }

    override suspend fun addOrUpdate(vehicleTrip: VehicleTrip): UpsertStatus = suspendTransaction {
        val entityId: EntityID<String> = EntityID(vehicleTrip.id, VehicleTripTable)
        val storedVehicleTrip: VehicleTripEntity? = VehicleTripEntity.findById(entityId)

        if (storedVehicleTrip == null) {
            addVehicleTrip(vehicleTrip)
        } else {
            updateVehicleTrip(vehicleTrip)
        }
    }

    @Suppress("DuplicatedCode")
    private fun addVehicleTrip(vehicleTrip: VehicleTrip): UpsertStatus {
        logger.trace("Adding vehicle trip with id: ${vehicleTrip.id}")

        VehicleTripEntity.new(vehicleTrip.id) {
            averageEnergyConsumption = vehicleTrip.averageEnergyConsumption
            averageEnergyConsumptionUnit = vehicleTrip.averageEnergyConsumptionUnit
            averageSpeed = vehicleTrip.averageSpeed
            createdTime = Clock.System.now()
            distance = vehicleTrip.distance
            distanceUnit = vehicleTrip.distanceUnit
            duration = vehicleTrip.duration
            durationUnit = vehicleTrip.durationUnit
            endAddress = vehicleTrip.endAddress
            endCity = vehicleTrip.endCity
            endTimestamp = vehicleTrip.endTimestamp
            energyRegenerated = vehicleTrip.energyRegenerated
            energyRegeneratedUnit = vehicleTrip.energyRegeneratedUnit
            speedUnit = vehicleTrip.speedUnit
            startAddress = vehicleTrip.startAddress
            startCity = vehicleTrip.startCity
            startTimestamp = vehicleTrip.startTimestamp
            totalDistance = vehicleTrip.totalDistance
        }

        logger.trace("Added vehicle trip with id: ${vehicleTrip.id}")

        return UpsertStatus.ADDED
    }

    /**
     * Note that actual database update is only performed if at least one column has changed the value, so
     * calling findByIdAndUpdate is not necessary doing any update if all columns have the same value in stored and new
     * guest.
     */
    @Suppress("DuplicatedCode")
    private fun updateVehicleTrip(vehicleTrip: VehicleTrip): UpsertStatus {
        logger.trace("Updating vehicle trip with id: ${vehicleTrip.id}")

        val updatedVehicleTrip: VehicleTripEntity =
            VehicleTripEntity.findById(vehicleTrip.id) ?: return UpsertStatus.NO_ACTION

        val status: UpsertStatus
        with(updatedVehicleTrip) {
            averageEnergyConsumption = vehicleTrip.averageEnergyConsumption
            averageEnergyConsumptionUnit = vehicleTrip.averageEnergyConsumptionUnit
            averageSpeed = vehicleTrip.averageSpeed
            createdTime = Clock.System.now()
            distance = vehicleTrip.distance
            distanceUnit = vehicleTrip.distanceUnit
            duration = vehicleTrip.duration
            durationUnit = vehicleTrip.durationUnit
            endAddress = vehicleTrip.endAddress
            endCity = vehicleTrip.endCity
            endTimestamp = vehicleTrip.endTimestamp
            energyRegenerated = vehicleTrip.energyRegenerated
            energyRegeneratedUnit = vehicleTrip.energyRegeneratedUnit
            speedUnit = vehicleTrip.speedUnit
            startAddress = vehicleTrip.startAddress
            startCity = vehicleTrip.startCity
            startTimestamp = vehicleTrip.startTimestamp
            totalDistance = vehicleTrip.totalDistance
        }

        val isDirty: Boolean = updatedVehicleTrip.writeValues.isNotEmpty()

        if (isDirty) {
            updatedVehicleTrip.version = updatedVehicleTrip.version.inc()
            updatedVehicleTrip.updatedTime = Clock.System.now()

            logger.trace("Updated vehicle trip with id: ${vehicleTrip.id}")

            status = UpsertStatus.UPDATED
        } else {
            logger.trace("No changes detected for vehicle trip with id: ${vehicleTrip.id}")

            status = UpsertStatus.NO_ACTION
        }

        return status
    }
}
