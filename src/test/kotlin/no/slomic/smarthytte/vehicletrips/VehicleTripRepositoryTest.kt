package no.slomic.smarthytte.vehicletrips

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.equality.shouldBeEqualToComparingFields
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import no.slomic.smarthytte.BaseDbTest
import org.jetbrains.exposed.sql.transactions.transaction

val vehicleTrip = createTrip(
    startCity = "osloCity",
    endCity = "osloCity",
    startTime = "2025-01-01T11:15:00+01:00",
    endTime = "2025-01-01T11:22:00+01:00",
)

class VehicleTripRepositoryTest :
    BaseDbTest({
        val repository: VehicleTripRepository = SqliteVehicleTripRepository()

        "add or update with new id should add new vehicle trip" {
            val returnedVehicleTrip = repository.addOrUpdate(vehicleTrip)
            returnedVehicleTrip.shouldBeEqualToComparingFields(vehicleTrip)

            transaction {
                val allVehicleTrips: List<VehicleTripEntity> = VehicleTripEntity.all().toList()
                allVehicleTrips shouldHaveSize 1

                val readVehicleTrip: VehicleTripEntity = allVehicleTrips.first()
                readVehicleTrip.shouldBeEqualToTrip(
                    other = vehicleTrip,
                    expectedVersion = 1,
                    shouldCreatedTimeBeNull = false,
                    shouldUpdatedTimeBeNull = true,
                )
            }
        }

        "add or update with existing id should update the existing vehicle trip" {
            repository.addOrUpdate(vehicleTrip)

            val updatedVehicleTrip = vehicleTrip.copy(endCity = "new end city")
            val returnedVehicleTrip = repository.addOrUpdate(updatedVehicleTrip)
            returnedVehicleTrip.shouldBeEqualToComparingFields(updatedVehicleTrip)

            transaction {
                val allVehicleTrips: List<VehicleTripEntity> = VehicleTripEntity.all().toList()
                allVehicleTrips shouldHaveSize 1

                val readVehicleTrip: VehicleTripEntity = allVehicleTrips.first()
                readVehicleTrip.shouldBeEqualToTrip(
                    other = updatedVehicleTrip,
                    expectedVersion = 2,
                    shouldCreatedTimeBeNull = false,
                    shouldUpdatedTimeBeNull = false,
                )
            }
        }

        "add or update with existing id without property changes should not update the existing vehicle trip" {
            repository.addOrUpdate(vehicleTrip)

            val updatedVehicleTrip = vehicleTrip
            val returnedVehicleTrip = repository.addOrUpdate(updatedVehicleTrip)
            returnedVehicleTrip.shouldBeEqualToComparingFields(updatedVehicleTrip)

            transaction {
                val allVehicleTrips: List<VehicleTripEntity> = VehicleTripEntity.all().toList()
                allVehicleTrips shouldHaveSize 1

                val readVehicleTrip: VehicleTripEntity = allVehicleTrips.first()
                readVehicleTrip.shouldBeEqualToTrip(
                    other = updatedVehicleTrip,
                    expectedVersion = 1,
                    shouldCreatedTimeBeNull = false,
                    shouldUpdatedTimeBeNull = true,
                )
            }
        }
    })

private fun VehicleTripEntity.shouldBeEqualToTrip(
    other: VehicleTrip,
    expectedVersion: Short,
    shouldCreatedTimeBeNull: Boolean,
    shouldUpdatedTimeBeNull: Boolean,
) {
    id.value shouldBe other.id
    averageEnergyConsumption shouldBe other.averageEnergyConsumption
    averageEnergyConsumptionUnit shouldBe other.averageEnergyConsumptionUnit
    averageSpeed shouldBe other.averageSpeed
    distance shouldBe other.distance
    distanceUnit shouldBe other.distanceUnit
    duration shouldBe other.duration
    durationUnit shouldBe other.durationUnit
    endAddress shouldBe other.endAddress
    endCity shouldBe other.endCity
    endTime shouldBe other.endTime
    energyRegenerated shouldBe other.energyRegenerated
    energyRegeneratedUnit shouldBe other.energyRegeneratedUnit
    speedUnit shouldBe other.speedUnit
    startAddress shouldBe other.startAddress
    startCity shouldBe other.startCity
    startTime shouldBe other.startTime
    totalDistance shouldBe other.totalDistance

    if (shouldCreatedTimeBeNull) {
        createdTime.shouldBeNull()
    } else {
        createdTime.shouldNotBeNull()
    }

    if (shouldUpdatedTimeBeNull) {
        updatedTime.shouldBeNull()
    } else {
        updatedTime.shouldNotBeNull()
    }

    version shouldBe expectedVersion
}
