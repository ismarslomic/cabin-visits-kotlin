package no.slomic.smarthytte.vehicletrip

import io.ktor.util.logging.KtorSimpleLogger
import io.ktor.util.logging.Logger
import kotlinx.datetime.Clock
import no.slomic.smarthytte.common.suspendTransaction
import org.jetbrains.exposed.dao.id.EntityID

class SqliteVehicleTripRepository : VehicleTripRepository {
    private val logger: Logger = KtorSimpleLogger(SqliteVehicleTripRepository::class.java.name)

    override suspend fun addOrUpdate(vehicleTrip: VehicleTrip) = suspendTransaction {
        val entityId: EntityID<String> = EntityID(vehicleTrip.journeyId.toString(), VehicleTripTable)
        val storedVehicleTrip: VehicleTripEntity? = VehicleTripEntity.findById(entityId)

        if (storedVehicleTrip == null) {
            addVehicleTrip(vehicleTrip)
        } else {
            updateVehicleTrip(vehicleTrip)
        }
    }

    private fun addVehicleTrip(vehicleTrip: VehicleTrip) {
        logger.info("Adding vehicle trip with id: ${vehicleTrip.journeyId}")

        VehicleTripEntity.new(vehicleTrip.journeyId.toString()) {
            averageEnergyConsumption = vehicleTrip.averageEnergyConsumption
            averageEnergyConsumptionUnit = vehicleTrip.averageEnergyConsumptionUnit
            averageSpeed = vehicleTrip.tripAverageSpeed
            distance = vehicleTrip.tripDistance
            distanceUnit = vehicleTrip.distanceUnit
            duration = vehicleTrip.tripDuration
            durationUnit = vehicleTrip.distanceUnit
            endAddress = vehicleTrip.endAddress
            endCity = vehicleTrip.endCity
            endDate = vehicleTrip.endDateInUserFormat
            endTime = vehicleTrip.endTimeInUserFormat
            energyRegenerated = vehicleTrip.energyRegenerated
            energyRegeneratedUnit = vehicleTrip.energyRegeneratedUnit
            speedUnit = vehicleTrip.speedUnit
            startAddress = vehicleTrip.startAddress
            startCity = vehicleTrip.startCity
            startDate = vehicleTrip.startDateInUserFormat
            startTime = vehicleTrip.startTimeInUserFormat
            totalDistance = vehicleTrip.totalDistance
            created = Clock.System.now()
        }

        logger.info("Added vehicle trip with id: ${vehicleTrip.journeyId}")
    }

    /**
     * Note that actual database update is only performed if at least one column has changed the value, so
     * calling findByIdAndUpdate is not necessary doing any update if all columns have the same value in stored and new
     * guest.
     */
    private fun updateVehicleTrip(vehicleTrip: VehicleTrip) {
        logger.info("Updating vehicle trip with id: ${vehicleTrip.journeyId}")

        val updatedVehicleTrip: VehicleTripEntity =
            VehicleTripEntity.findById(vehicleTrip.journeyId.toString()) ?: return

        with(updatedVehicleTrip) {
            averageEnergyConsumption = vehicleTrip.averageEnergyConsumption
            averageEnergyConsumptionUnit = vehicleTrip.averageEnergyConsumptionUnit
            averageSpeed = vehicleTrip.tripAverageSpeed
            distance = vehicleTrip.tripDistance
            distanceUnit = vehicleTrip.distanceUnit
            duration = vehicleTrip.tripDuration
            durationUnit = vehicleTrip.distanceUnit
            endAddress = vehicleTrip.endAddress
            endCity = vehicleTrip.endCity
            endDate = vehicleTrip.endDateInUserFormat
            endTime = vehicleTrip.endTimeInUserFormat
            energyRegenerated = vehicleTrip.energyRegenerated
            energyRegeneratedUnit = vehicleTrip.energyRegeneratedUnit
            speedUnit = vehicleTrip.speedUnit
            startAddress = vehicleTrip.startAddress
            startCity = vehicleTrip.startCity
            startDate = vehicleTrip.startDateInUserFormat
            startTime = vehicleTrip.startTimeInUserFormat
            totalDistance = vehicleTrip.totalDistance
        }

        val isDirty: Boolean = updatedVehicleTrip.writeValues.isNotEmpty()

        if (isDirty) {
            updatedVehicleTrip.version = updatedVehicleTrip.version.inc()
            updatedVehicleTrip.updated = Clock.System.now()

            logger.info("Updated vehicle trip with id: ${vehicleTrip.journeyId}")
        } else {
            logger.info("No changes detected for vehicle trip with id: ${vehicleTrip.journeyId}")
        }
    }
}
