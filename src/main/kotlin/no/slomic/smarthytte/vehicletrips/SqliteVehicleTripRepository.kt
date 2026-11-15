package no.slomic.smarthytte.vehicletrips

import io.ktor.util.logging.KtorSimpleLogger
import io.ktor.util.logging.Logger
import no.slomic.smarthytte.common.PersistenceResult
import no.slomic.smarthytte.common.markCreated
import no.slomic.smarthytte.common.markUpdated
import no.slomic.smarthytte.common.suspendReadTransaction
import no.slomic.smarthytte.common.suspendTransaction
import no.slomic.smarthytte.common.truncatedToMillis
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.lessEq
import kotlin.time.Instant

class SqliteVehicleTripRepository : VehicleTripRepository {
    private val logger: Logger = KtorSimpleLogger(SqliteVehicleTripRepository::class.java.name)

    override suspend fun allVehicleTrips(): List<VehicleTrip> = suspendReadTransaction {
        VehicleTripEntity.all().sortedBy { it.startTime }.map(::daoToModel)
    }

    override suspend fun findByEndTimeBetween(from: Instant, to: Instant): List<VehicleTrip> = suspendReadTransaction {
        VehicleTripEntity
            .find { (VehicleTripTable.endTime greaterEq from) and (VehicleTripTable.endTime lessEq to) }
            .sortedBy { it.endTime }
            .map(::daoToModel)
    }

    override suspend fun findByDataQuality(quality: DataQuality): List<VehicleTrip> = suspendReadTransaction {
        VehicleTripEntity.find { VehicleTripTable.dataQuality eq quality }.sortedBy { it.startTime }.map(::daoToModel)
    }

    override suspend fun setJlrData(
        id: String,
        jlrJourneyId: String,
        startPoint: GeoPoint,
        endPoint: GeoPoint,
        actor: String,
    ): PersistenceResult = suspendTransaction {
        val trip = VehicleTripEntity.findById(id)
        if (trip == null) {
            PersistenceResult.NO_ACTION
        } else {
            trip.jlrJourneyId = jlrJourneyId
            trip.startLatitude = startPoint.latitude
            trip.startLongitude = startPoint.longitude
            trip.endLatitude = endPoint.latitude
            trip.endLongitude = endPoint.longitude
            markChanged(trip, actor)
        }
    }

    override suspend fun updatePlaces(
        id: String,
        places: TripPlaces,
        quality: DataQuality,
        actor: String,
    ): PersistenceResult = suspendTransaction {
        val trip = VehicleTripEntity.findById(id)
        if (trip == null) {
            PersistenceResult.NO_ACTION
        } else {
            trip.startCity = places.startCity
            trip.startAddress = places.startAddress
            trip.endCity = places.endCity
            trip.endAddress = places.endAddress
            trip.dataQuality = quality
            markChanged(trip, actor)
        }
    }

    private fun markChanged(trip: VehicleTripEntity, actor: String): PersistenceResult =
        if (trip.writeValues.isNotEmpty()) {
            trip.markUpdated(actor)
            PersistenceResult.UPDATED
        } else {
            PersistenceResult.NO_ACTION
        }

    override suspend fun addOrUpdate(vehicleTrip: VehicleTrip, actor: String): PersistenceResult = suspendTransaction {
        val entityId: EntityID<String> = EntityID(vehicleTrip.id, VehicleTripTable)
        val storedVehicleTrip: VehicleTripEntity? = VehicleTripEntity.findById(entityId)

        if (storedVehicleTrip == null) {
            addVehicleTrip(vehicleTrip, actor)
        } else {
            updateVehicleTrip(vehicleTrip, actor)
        }
    }

    @Suppress("DuplicatedCode")
    private fun addVehicleTrip(vehicleTrip: VehicleTrip, actor: String): PersistenceResult {
        logger.trace("Adding vehicle trip with id: ${vehicleTrip.id}")

        VehicleTripEntity.new(vehicleTrip.id) {
            markCreated(actor)
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
            dataQuality = vehicleTrip.dataQuality
            jlrJourneyId = vehicleTrip.jlrJourneyId
            startLatitude = vehicleTrip.startPoint?.latitude
            startLongitude = vehicleTrip.startPoint?.longitude
            endLatitude = vehicleTrip.endPoint?.latitude
            endLongitude = vehicleTrip.endPoint?.longitude
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
    private fun updateVehicleTrip(vehicleTrip: VehicleTrip, actor: String): PersistenceResult {
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
            updatedVehicleTrip.markUpdated(actor)

            logger.trace("Updated vehicle trip with id: ${vehicleTrip.id}")
            PersistenceResult.UPDATED
        } else {
            logger.trace("No changes detected for vehicle trip with id: ${vehicleTrip.id}")
            PersistenceResult.NO_ACTION
        }
    }
}
