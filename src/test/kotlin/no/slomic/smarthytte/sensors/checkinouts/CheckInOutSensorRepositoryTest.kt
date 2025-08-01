package no.slomic.smarthytte.sensors.checkinouts

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.datetime.Instant
import no.slomic.smarthytte.common.PersistenceResult
import no.slomic.smarthytte.utils.TestDbSetup
import org.jetbrains.exposed.sql.transactions.transaction

val checkInSensor = CheckInOutSensor(
    id = "foo-bar",
    time = Instant.parse("2025-01-14T18:00:00Z"),
    status = CheckInStatus.CHECKED_IN,
)

class CheckInOutSensorRepositoryTest :
    StringSpec({
        val testDbSetup = TestDbSetup()

        beforeTest {
            testDbSetup.setupDb()
        }

        afterTest {
            testDbSetup.teardownDb()
        }

        val repository: CheckInOutSensorRepository = SqliteCheckInOutSensorRepository()

        "add or update with new id should add new check in/out" {
            val persistenceResult = repository.addOrUpdate(checkInSensor)
            persistenceResult shouldBe PersistenceResult.ADDED

            transaction {
                val allCheckInOutSensors: List<CheckInOutSensorEntity> = CheckInOutSensorEntity.all().toList()
                allCheckInOutSensors shouldHaveSize 1

                val readCheckInOutSensor: CheckInOutSensorEntity = allCheckInOutSensors.first()
                readCheckInOutSensor.shouldBeEqualToCheckInOutSensor(
                    other = checkInSensor,
                    expectedVersion = 1,
                    shouldCreatedTimeBeNull = false,
                    shouldUpdatedTimeBeNull = true,
                )
            }
        }

        "add or update with existing id should update the existing check in/out" {
            repository.addOrUpdate(checkInSensor)

            val updatedCheckInSensor = checkInSensor.copy(status = CheckInStatus.CHECKED_OUT)
            val persistenceResult = repository.addOrUpdate(updatedCheckInSensor)
            persistenceResult shouldBe PersistenceResult.UPDATED

            transaction {
                val allCheckInOutSensors: List<CheckInOutSensorEntity> = CheckInOutSensorEntity.all().toList()
                allCheckInOutSensors shouldHaveSize 1

                val readCheckInOutSensor: CheckInOutSensorEntity = allCheckInOutSensors.first()
                readCheckInOutSensor.shouldBeEqualToCheckInOutSensor(
                    other = updatedCheckInSensor,
                    expectedVersion = 2,
                    shouldCreatedTimeBeNull = false,
                    shouldUpdatedTimeBeNull = false,
                )
            }
        }

        "add or update with existing id without property changes should not update the existing check in/out" {
            repository.addOrUpdate(checkInSensor)

            val updatedCheckInSensor = checkInSensor
            val persistenceResult = repository.addOrUpdate(updatedCheckInSensor)
            persistenceResult shouldBe PersistenceResult.NO_ACTION

            transaction {
                val allCheckInOutSensors: List<CheckInOutSensorEntity> = CheckInOutSensorEntity.all().toList()
                allCheckInOutSensors shouldHaveSize 1

                val readCheckInOutSensor: CheckInOutSensorEntity = allCheckInOutSensors.first()
                readCheckInOutSensor.shouldBeEqualToCheckInOutSensor(
                    other = updatedCheckInSensor,
                    expectedVersion = 1,
                    shouldCreatedTimeBeNull = false,
                    shouldUpdatedTimeBeNull = true,
                )
            }
        }
    })

private fun CheckInOutSensorEntity.shouldBeEqualToCheckInOutSensor(
    other: CheckInOutSensor,
    expectedVersion: Short,
    shouldCreatedTimeBeNull: Boolean,
    shouldUpdatedTimeBeNull: Boolean,
) {
    id.value shouldBe other.id
    status shouldBe other.status

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
