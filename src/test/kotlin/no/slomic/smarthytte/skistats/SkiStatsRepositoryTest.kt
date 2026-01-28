package no.slomic.smarthytte.skistats

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import no.slomic.smarthytte.common.PersistenceResult
import no.slomic.smarthytte.utils.TestDbSetup
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class SkiStatsRepositoryTest :
    StringSpec({
        val testDbSetup = TestDbSetup()

        beforeTest {
            testDbSetup.setupDb()
        }

        afterTest {
            testDbSetup.teardownDb()
        }

        val repository: SkiStatsRepository = SqliteSkiStatsRepository()
        val profileId = "user-123"
        val tokens = SkiStatsTokens(
            accessToken = "access-token-123",
            refreshToken = "refresh-token-123",
            expiresAtEpochSeconds = 1234567890L,
        )

        "add or update with new id should add new tokens and return persistence result ADDED" {
            val persistenceResult = repository.addOrUpdateTokens(profileId, tokens)
            persistenceResult shouldBe PersistenceResult.ADDED

            transaction {
                val allTokens: List<SkiStatsTokenEntity> = SkiStatsTokenEntity.all().toList()
                allTokens shouldHaveSize 1

                val storedTokens: SkiStatsTokenEntity = allTokens.first()
                storedTokens.shouldBeEqualTo(
                    id = profileId,
                    tokens = tokens,
                    expectedVersion = 1,
                    shouldCreatedTimeBeNull = false,
                    shouldUpdatedTimeBeNull = true,
                )
            }
        }

        "add or update with existing id should update the existing tokens and return persistence result UPDATED" {
            repository.addOrUpdateTokens(profileId, tokens)

            val updatedTokens = tokens.copy(accessToken = "new-access-token")
            val updatePersistenceResult = repository.addOrUpdateTokens(profileId, updatedTokens)
            updatePersistenceResult shouldBe PersistenceResult.UPDATED

            transaction {
                val allTokens: List<SkiStatsTokenEntity> = SkiStatsTokenEntity.all().toList()
                allTokens shouldHaveSize 1

                val storedTokens: SkiStatsTokenEntity = allTokens.first()
                storedTokens.shouldBeEqualTo(
                    id = profileId,
                    tokens = updatedTokens,
                    expectedVersion = 2,
                    shouldCreatedTimeBeNull = false,
                    shouldUpdatedTimeBeNull = false,
                )
            }
        }

        "update existing tokens without property changes should not update and return NO_ACTION" {
            repository.addOrUpdateTokens(profileId, tokens)
            val updatePersistenceResult = repository.addOrUpdateTokens(profileId, tokens)
            updatePersistenceResult shouldBe PersistenceResult.NO_ACTION

            transaction {
                val allTokens: List<SkiStatsTokenEntity> = SkiStatsTokenEntity.all().toList()
                allTokens shouldHaveSize 1

                val storedTokens: SkiStatsTokenEntity = allTokens.first()
                storedTokens.shouldBeEqualTo(
                    id = profileId,
                    tokens = tokens,
                    expectedVersion = 1,
                    shouldCreatedTimeBeNull = false,
                    shouldUpdatedTimeBeNull = true,
                )
            }
        }

        "reading non existing tokens should return null" {
            val storedTokens: SkiStatsTokens? = repository.tokensByProfile("non-existing-id")
            storedTokens.shouldBeNull()
        }

        "reading existing tokens should return the tokens" {
            repository.addOrUpdateTokens(profileId, tokens)

            val storedTokens: SkiStatsTokens? = repository.tokensByProfile(profileId)
            storedTokens shouldBe tokens
        }
    })

private fun SkiStatsTokenEntity.shouldBeEqualTo(
    id: String,
    tokens: SkiStatsTokens,
    expectedVersion: Short,
    shouldCreatedTimeBeNull: Boolean,
    shouldUpdatedTimeBeNull: Boolean,
) {
    this.id.value shouldBe id
    this.accessToken shouldBe tokens.accessToken
    this.refreshToken shouldBe tokens.refreshToken
    this.expiresAtEpochSeconds shouldBe tokens.expiresAtEpochSeconds

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
