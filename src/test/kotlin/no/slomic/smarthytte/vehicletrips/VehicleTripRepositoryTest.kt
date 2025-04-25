package no.slomic.smarthytte.vehicletrips

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.equality.shouldBeEqualToComparingFields
import io.kotest.matchers.nulls.shouldBeNull
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

        "add or update with new id should add new guest" {
            val returnedVehicleTrip = repository.addOrUpdate(vehicleTrip)
            returnedVehicleTrip.shouldBeEqualToComparingFields(vehicleTrip)

            transaction {
                val allVehicleTrips: List<VehicleTripEntity> = VehicleTripEntity.all().toList()
                allVehicleTrips shouldHaveSize 1

                val readVehicleTrip: VehicleTripEntity = allVehicleTrips.first()
                readVehicleTrip.id.value shouldBe vehicleTrip.id
                readVehicleTrip.averageEnergyConsumption shouldBe vehicleTrip.averageEnergyConsumption
                readVehicleTrip.averageEnergyConsumptionUnit shouldBe vehicleTrip.averageEnergyConsumptionUnit
                readVehicleTrip.averageSpeed shouldBe vehicleTrip.averageSpeed
                readVehicleTrip.distance shouldBe vehicleTrip.distance
                readVehicleTrip.distanceUnit shouldBe vehicleTrip.distanceUnit
                readVehicleTrip.duration shouldBe vehicleTrip.duration
                readVehicleTrip.durationUnit shouldBe vehicleTrip.durationUnit
                readVehicleTrip.endAddress shouldBe vehicleTrip.endAddress
                readVehicleTrip.endCity shouldBe vehicleTrip.endCity
                readVehicleTrip.endTime shouldBe vehicleTrip.endTime
                readVehicleTrip.energyRegenerated shouldBe vehicleTrip.energyRegenerated
                readVehicleTrip.energyRegeneratedUnit shouldBe vehicleTrip.energyRegeneratedUnit
                readVehicleTrip.speedUnit shouldBe vehicleTrip.speedUnit
                readVehicleTrip.startAddress shouldBe vehicleTrip.startAddress
                readVehicleTrip.startCity shouldBe vehicleTrip.startCity
                readVehicleTrip.startTime shouldBe vehicleTrip.startTime
                readVehicleTrip.totalDistance shouldBe vehicleTrip.totalDistance
                readVehicleTrip.updatedTime.shouldBeNull()
                readVehicleTrip.version shouldBe 1
            }
        }

        /*
        "add or update with existing id should update the existing guest" {
            repository.addOrUpdate(vehicleTrip)

            val updatedGuest = vehicleTrip.copy(firstName = "John 2")
            val returnedGuest = repository.addOrUpdate(updatedGuest)
            returnedGuest.shouldBeEqualToComparingFields(updatedGuest)

            transaction {
                val allGuests: List<GuestEntity> = GuestEntity.all().toList()
                allGuests shouldHaveSize 1

                val readGuest: GuestEntity = allGuests.first()
                readGuest.id.value shouldBe updatedGuest.id
                readGuest.firstName shouldBe updatedGuest.firstName
                readGuest.lastName shouldBe updatedGuest.lastName
                readGuest.birthYear shouldBe updatedGuest.birthYear
                readGuest.email shouldBe updatedGuest.email
                readGuest.gender shouldBe updatedGuest.gender
                readGuest.updatedTime.shouldNotBeNull()
                readGuest.version shouldBe 2
            }
        }

        "add or update with existing id without property changes should not update the existing guest" {
            repository.addOrUpdate(vehicleTrip)

            val updatedGuest = vehicleTrip
            val returnedGuest = repository.addOrUpdate(updatedGuest)
            returnedGuest.shouldBeEqualToComparingFields(updatedGuest)

            transaction {
                val allGuests: List<GuestEntity> = GuestEntity.all().toList()
                allGuests shouldHaveSize 1

                val readGuest: GuestEntity = allGuests.first()
                readGuest.id.value shouldBe updatedGuest.id
                readGuest.firstName shouldBe updatedGuest.firstName
                readGuest.lastName shouldBe updatedGuest.lastName
                readGuest.birthYear shouldBe updatedGuest.birthYear
                readGuest.email shouldBe updatedGuest.email
                readGuest.gender shouldBe updatedGuest.gender
                readGuest.updatedTime.shouldBeNull()
                readGuest.version shouldBe 1
            }
        }

        "delete should remove event guests from the intermediate table (cascade)" {
            val calenderEventRepository: ReservationRepository = SqliteReservationRepository()
            repository.addOrUpdate(vehicleTrip)
            val guest2 = vehicleTrip.copy(id = "john2", firstName = "John2", lastName = "Doe2")
            repository.addOrUpdate(guest2)

            val eventWithGuest = event.copy(
                guestIds = listOf(vehicleTrip.id, guest2.id),
            )
            calenderEventRepository.addOrUpdate(eventWithGuest)
            calenderEventRepository.reservationById(eventWithGuest.id).shouldNotBeNull()

            transaction {
                ReservationGuestTable.selectAll().toList() shouldHaveSize 2
                GuestEntity.findById(EntityID(vehicleTrip.id, GuestTable))!!.delete()
                ReservationGuestTable.selectAll().toList() shouldHaveSize 1
                GuestEntity.findById(EntityID(guest2.id, GuestTable))!!.delete()
                ReservationGuestTable.selectAll().toList().shouldBeEmpty()
            }
        }*/
    })
