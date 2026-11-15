package no.slomic.smarthytte.vehicletrips

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import no.slomic.smarthytte.common.PersistenceResult
import no.slomic.smarthytte.common.SYSTEM_ACTOR
import no.slomic.smarthytte.utils.TestDbSetup
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.time.Instant

val vehicleTrip = createTrip(
    startCity = "osloCity",
    endCity = "osloCity",
    startTime = "2025-01-01T11:15:00+01:00",
    endTime = "2025-01-01T11:22:00+01:00",
)

class VehicleTripRepositoryTest :
    StringSpec({
        val testDbSetup = TestDbSetup()

        beforeTest {
            testDbSetup.setupDb()
        }

        afterTest {
            testDbSetup.teardownDb()
        }

        val repository: VehicleTripRepository = SqliteVehicleTripRepository()

        "add or update with new id should add new vehicle trip" {
            val persistenceResult = repository.addOrUpdate(vehicleTrip, SYSTEM_ACTOR)
            persistenceResult shouldBe PersistenceResult.ADDED

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

        "add or update should store a trip without energy and places, with its JLR data" {
            val trip = createTrip(
                startCity = null,
                endCity = null,
                startTime = "2026-11-01T10:00:00Z",
                endTime = "2026-11-01T11:00:00Z",
                averageEnergyConsumption = null,
                energyRegenerated = null,
                dataQuality = DataQuality.PLACE_MISSING,
                jlrJourneyId = "42",
                startPoint = GeoPoint(60.1, 9.1),
                endPoint = GeoPoint(60.2, 9.2),
            )

            repository.addOrUpdate(trip, SYSTEM_ACTOR) shouldBe PersistenceResult.ADDED

            repository.allVehicleTrips().single() shouldBe trip
        }

        "add or update on an existing trip should leave data quality, JLR journey id and coordinates untouched" {
            val trip = vehicleTrip.copy(
                dataQuality = DataQuality.PLACE_MISSING,
                jlrJourneyId = "42",
                startPoint = GeoPoint(60.1, 9.1),
                endPoint = GeoPoint(60.2, 9.2),
            )
            repository.addOrUpdate(trip, SYSTEM_ACTOR)

            // The archive file knows nothing about the JLR columns
            repository.addOrUpdate(vehicleTrip.copy(endCity = "new end city"), SYSTEM_ACTOR) shouldBe
                PersistenceResult.UPDATED

            repository.allVehicleTrips().single() shouldBe trip.copy(endCity = "new end city")
        }

        "find by end time between should return the trips in the inclusive range sorted by end time" {
            val before = createTrip("a", "b", "2026-11-01T08:00:00Z", "2026-11-01T09:00:00Z", id = "before")
            val atFrom = createTrip("a", "b", "2026-11-01T09:30:00Z", "2026-11-01T10:00:00Z", id = "atFrom")
            val atTo = createTrip("a", "b", "2026-11-01T10:30:00Z", "2026-11-01T11:00:00Z", id = "atTo")
            val after = createTrip("a", "b", "2026-11-01T11:30:00Z", "2026-11-01T12:00:00Z", id = "after")
            listOf(atTo, after, before, atFrom).forEach { repository.addOrUpdate(it, SYSTEM_ACTOR) }

            val found = repository.findByEndTimeBetween(
                from = Instant.parse("2026-11-01T10:00:00Z"),
                to = Instant.parse("2026-11-01T11:00:00Z"),
            )

            found.map { it.id } shouldBe listOf("atFrom", "atTo")
        }

        "find by data quality should return only the trips with that quality" {
            val missing = createTrip("a", null, "2026-11-01T09:30:00Z", "2026-11-01T10:00:00Z", id = "missing")
                .copy(dataQuality = DataQuality.PLACE_MISSING)
            repository.addOrUpdate(missing, SYSTEM_ACTOR)
            repository.addOrUpdate(vehicleTrip, SYSTEM_ACTOR)

            repository.findByDataQuality(DataQuality.PLACE_MISSING).map { it.id } shouldBe listOf("missing")
            repository.findByDataQuality(DataQuality.COMPLETE).map { it.id } shouldBe listOf(vehicleTrip.id)
        }

        "set JLR data should store the journey id and coordinates and change nothing else" {
            repository.addOrUpdate(vehicleTrip, SYSTEM_ACTOR)
            val start = GeoPoint(60.1, 9.1)
            val end = GeoPoint(60.2, 9.2)

            repository.setJlrData(vehicleTrip.id, "42", start, end, "jlr") shouldBe PersistenceResult.UPDATED
            repository.setJlrData(vehicleTrip.id, "42", start, end, "jlr") shouldBe PersistenceResult.NO_ACTION

            repository.allVehicleTrips().single() shouldBe
                vehicleTrip.copy(jlrJourneyId = "42", startPoint = start, endPoint = end)
            transaction { VehicleTripEntity.all().single().updatedBy shouldBe "jlr" }
        }

        "set JLR data on an unknown trip should do nothing" {
            repository.setJlrData("unknown", "42", GeoPoint(1.0, 2.0), GeoPoint(3.0, 4.0), "jlr") shouldBe
                PersistenceResult.NO_ACTION
        }

        "update places should store places and quality and change nothing else" {
            val missing = vehicleTrip.copy(startCity = null, endCity = null, dataQuality = DataQuality.PLACE_MISSING)
            repository.addOrUpdate(missing, SYSTEM_ACTOR)
            val places = TripPlaces(startCity = "Oslo", startAddress = "Gate 1", endCity = "Ullsåk", endAddress = null)

            repository.updatePlaces(missing.id, places, DataQuality.COMPLETE, "jlr") shouldBe PersistenceResult.UPDATED
            repository.updatePlaces(missing.id, places, DataQuality.COMPLETE, "jlr") shouldBe
                PersistenceResult.NO_ACTION

            repository.allVehicleTrips().single() shouldBe missing.copy(
                startCity = "Oslo",
                startAddress = "Gate 1",
                endCity = "Ullsåk",
                endAddress = null,
                dataQuality = DataQuality.COMPLETE,
            )
        }

        "update places on an unknown trip should do nothing" {
            repository.updatePlaces(
                "unknown",
                TripPlaces(null, null, null, null),
                DataQuality.PLACE_MISSING,
                "jlr",
            ) shouldBe
                PersistenceResult.NO_ACTION
        }

        "add or update should record the actor in created_by and, for a change, in updated_by" {
            repository.addOrUpdate(vehicleTrip, "importer")
            transaction {
                val created = VehicleTripEntity.all().single()
                created.createdBy shouldBe "importer"
                created.updatedBy.shouldBeNull()
            }

            repository.addOrUpdate(vehicleTrip.copy(endCity = "new end city"), "other") shouldBe
                PersistenceResult.UPDATED
            repository.addOrUpdate(vehicleTrip.copy(endCity = "new end city"), "third") shouldBe
                PersistenceResult.NO_ACTION

            transaction {
                val updated = VehicleTripEntity.all().single()
                updated.createdBy shouldBe "importer"
                updated.updatedBy shouldBe "other"
            }
        }

        "add or update with existing id should update the existing vehicle trip" {
            repository.addOrUpdate(vehicleTrip, SYSTEM_ACTOR)

            val updatedVehicleTrip = vehicleTrip.copy(endCity = "new end city")
            val persistenceResult = repository.addOrUpdate(updatedVehicleTrip, SYSTEM_ACTOR)
            persistenceResult shouldBe PersistenceResult.UPDATED

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
            repository.addOrUpdate(vehicleTrip, SYSTEM_ACTOR)

            val updatedVehicleTrip = vehicleTrip
            val persistenceResult = repository.addOrUpdate(updatedVehicleTrip, SYSTEM_ACTOR)
            persistenceResult shouldBe PersistenceResult.NO_ACTION

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
    notionId shouldBe other.notionId

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
