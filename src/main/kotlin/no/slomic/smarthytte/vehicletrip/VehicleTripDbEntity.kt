package no.slomic.smarthytte.vehicletrip

import kotlinx.datetime.Instant
import no.slomic.smarthytte.common.BaseEntity
import no.slomic.smarthytte.common.BaseIdTable
import org.jetbrains.exposed.dao.EntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.kotlin.datetime.duration
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp
import kotlin.time.Duration

object VehicleTripTable : BaseIdTable<String>(name = "vehicle_trip") {
    override val id: Column<EntityID<String>> = varchar("id", length = 50).entityId()
    val averageEnergyConsumption: Column<Double> = double(name = "avg_energy_consumption")
    val averageEnergyConsumptionUnit: Column<String> = varchar(name = "avg_energy_consumption_unit", length = 10)
    val averageSpeed: Column<Double> = double("avg_speed")
    val distance: Column<Double> = double(name = "distance")
    val distanceUnit: Column<String> = varchar(name = "distance_unit", length = 5)
    val duration: Column<Duration> = duration(name = "duration")
    val durationUnit: Column<String> = varchar(name = "duration_unit", length = 5)
    val endAddress: Column<String> = varchar(name = "end_address", length = 100)
    val endCity: Column<String> = varchar(name = "end_city", length = 30)
    val endTimestamp: Column<Instant> = timestamp(name = "end_timestamp")
    val energyRegenerated: Column<Double> = double(name = "energy_regenerated")
    val energyRegeneratedUnit: Column<String> = varchar(name = "energy_generated_unit", length = 5)
    val speedUnit: Column<String> = varchar(name = "speed_unit", length = 5)
    val startAddress: Column<String> = varchar(name = "start_address", length = 100)
    val startCity: Column<String> = varchar(name = "start_city", length = 30)
    val startTimestamp: Column<Instant> = timestamp(name = "start_timestamp")
    val totalDistance: Column<Double> = double(name = "total_distance")

    override val primaryKey = PrimaryKey(id, name = "pk_vehicle_trip_id")
}

class VehicleTripEntity(id: EntityID<String>) : BaseEntity<String>(id, VehicleTripTable) {
    companion object : EntityClass<String, VehicleTripEntity>(VehicleTripTable)

    var averageEnergyConsumption: Double by VehicleTripTable.averageEnergyConsumption
    var averageEnergyConsumptionUnit: String by VehicleTripTable.averageEnergyConsumptionUnit
    var averageSpeed: Double by VehicleTripTable.averageSpeed
    var distance: Double by VehicleTripTable.distance
    var distanceUnit: String by VehicleTripTable.distanceUnit
    var duration: Duration by VehicleTripTable.duration
    var durationUnit: String by VehicleTripTable.durationUnit
    var endAddress: String by VehicleTripTable.endAddress
    var endCity: String by VehicleTripTable.endCity
    var endTimestamp: Instant by VehicleTripTable.endTimestamp
    var energyRegenerated: Double by VehicleTripTable.energyRegenerated
    var energyRegeneratedUnit: String by VehicleTripTable.energyRegeneratedUnit
    var speedUnit: String by VehicleTripTable.speedUnit
    var startAddress: String by VehicleTripTable.startAddress
    var startCity: String by VehicleTripTable.startCity
    var startTimestamp: Instant by VehicleTripTable.startTimestamp
    var totalDistance: Double by VehicleTripTable.totalDistance
}

fun daoToModel(dao: VehicleTripEntity) = VehicleTrip(
    averageEnergyConsumption = dao.averageEnergyConsumption,
    averageEnergyConsumptionUnit = dao.averageEnergyConsumptionUnit,
    averageSpeed = dao.averageSpeed,
    distance = dao.distance,
    distanceUnit = dao.distanceUnit,
    duration = dao.duration,
    durationUnit = dao.durationUnit,
    endAddress = dao.endAddress,
    endCity = dao.endCity,
    endTimestamp = dao.endTimestamp,
    energyRegenerated = dao.energyRegenerated,
    energyRegeneratedUnit = dao.energyRegeneratedUnit,
    id = dao.id.value,
    speedUnit = dao.speedUnit,
    startAddress = dao.startAddress,
    startCity = dao.startCity,
    startTimestamp = dao.startTimestamp,
    totalDistance = dao.totalDistance,
)
