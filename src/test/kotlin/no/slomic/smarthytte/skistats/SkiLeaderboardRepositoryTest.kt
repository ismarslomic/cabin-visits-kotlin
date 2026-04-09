package no.slomic.smarthytte.skistats

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import no.slomic.smarthytte.common.PersistenceResult
import no.slomic.smarthytte.utils.TestDbSetup
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class SkiLeaderboardRepositoryTest :
    StringSpec({
        val testDbSetup = TestDbSetup()

        beforeTest {
            testDbSetup.setupDb()
        }

        afterTest {
            testDbSetup.teardownDb()
        }

        val repository: SkiStatsRepository = SqliteSkiStatsRepository()

        // --- ski_stats_profile tests ---

        "add new profile should return ADDED" {
            val profile = createSkiProfile()
            val result = repository.addOrUpdateProfile(profile)
            result shouldBe PersistenceResult.ADDED

            transaction {
                val all = SkiProfileEntity.all().toList()
                all shouldHaveSize 1
                all.first().shouldBeEqualToProfile(
                    profile = profile,
                    expectedVersion = 1,
                    shouldCreatedTimeBeNull = false,
                    shouldUpdatedTimeBeNull = true,
                )
            }
        }

        "update existing profile with name change should return UPDATED and increment version" {
            val profile = createSkiProfile()
            repository.addOrUpdateProfile(profile)

            val updated = profile.copy(name = "Ismar Updated")
            val result = repository.addOrUpdateProfile(updated)
            result shouldBe PersistenceResult.UPDATED

            transaction {
                val all = SkiProfileEntity.all().toList()
                all shouldHaveSize 1
                all.first().shouldBeEqualToProfile(
                    profile = updated,
                    expectedVersion = 2,
                    shouldCreatedTimeBeNull = false,
                    shouldUpdatedTimeBeNull = false,
                )
            }
        }

        "update existing profile without changes should return NO_ACTION" {
            val profile = createSkiProfile()
            repository.addOrUpdateProfile(profile)
            val result = repository.addOrUpdateProfile(profile)
            result shouldBe PersistenceResult.NO_ACTION

            transaction {
                val all = SkiProfileEntity.all().toList()
                all shouldHaveSize 1
                all.first().shouldBeEqualToProfile(
                    profile = profile,
                    expectedVersion = 1,
                    shouldCreatedTimeBeNull = false,
                    shouldUpdatedTimeBeNull = true,
                )
            }
        }

        // --- ski_stats_leaderboard_entry tests ---

        "add new leaderboard entry should return ADDED" {
            val profile = createSkiProfile()
            repository.addOrUpdateProfile(profile)

            val entry = createSkiLeaderboardEntry(entryUserId = profile.id)
            val result = repository.addOrUpdateLeaderboardEntry(entry)
            result shouldBe PersistenceResult.ADDED

            transaction {
                val all = SkiLeaderboardEntryEntity.all().toList()
                all shouldHaveSize 1
                all.first().shouldBeEqualToEntry(
                    entry = entry,
                    expectedVersion = 1,
                    shouldCreatedTimeBeNull = false,
                    shouldUpdatedTimeBeNull = true,
                )
            }
        }

        "update existing entry with new position should return UPDATED" {
            val profile = createSkiProfile()
            repository.addOrUpdateProfile(profile)
            val entry = createSkiLeaderboardEntry(entryUserId = profile.id)
            repository.addOrUpdateLeaderboardEntry(entry)

            val updated = entry.copy(position = 3, dropHeightInMeter = 5000)
            val result = repository.addOrUpdateLeaderboardEntry(updated)
            result shouldBe PersistenceResult.UPDATED

            transaction {
                val all = SkiLeaderboardEntryEntity.all().toList()
                all shouldHaveSize 1
                all.first().shouldBeEqualToEntry(
                    entry = updated,
                    expectedVersion = 2,
                    shouldCreatedTimeBeNull = false,
                    shouldUpdatedTimeBeNull = false,
                )
            }
        }

        "update existing entry without changes should return NO_ACTION" {
            val profile = createSkiProfile()
            repository.addOrUpdateProfile(profile)
            val entry = createSkiLeaderboardEntry(entryUserId = profile.id)
            repository.addOrUpdateLeaderboardEntry(entry)

            val result = repository.addOrUpdateLeaderboardEntry(entry)
            result shouldBe PersistenceResult.NO_ACTION

            transaction {
                SkiLeaderboardEntryEntity.all().toList() shouldHaveSize 1
            }
        }

        "read entries by profileId and periodType should return matching entries only" {
            val profile1 = createSkiProfile(id = "ABCD12345")
            val profile2 = createSkiProfile(id = "EFGH12345", name = "Mary Doe")
            repository.addOrUpdateProfile(profile1)
            repository.addOrUpdateProfile(profile2)

            val dayEntry1 =
                createSkiLeaderboardEntry(profileId = "ismar", entryUserId = profile1.id)
            val dayEntry2 = createSkiLeaderboardEntry(
                profileId = "ismar",
                entryUserId = profile2.id,
                position = 2,
            )
            val weekEntry = createSkiLeaderboardEntry(
                profileId = "ismar",
                period = createSkiLeaderboardPeriod(type = PeriodType.WEEK, value = "2907"),
                entryUserId = profile1.id,
            )
            repository.addOrUpdateLeaderboardEntry(dayEntry1)
            repository.addOrUpdateLeaderboardEntry(dayEntry2)
            repository.addOrUpdateLeaderboardEntry(weekEntry)

            val dayEntries = repository.leaderboardEntriesByProfileAndPeriodType("ismar", PeriodType.DAY)
            dayEntries shouldHaveSize 2
            dayEntries.map { it.entryUserId }.toSet() shouldBe setOf(profile1.id, profile2.id)

            val weekEntries = repository.leaderboardEntriesByProfileAndPeriodType("ismar", PeriodType.WEEK)
            weekEntries shouldHaveSize 1
            weekEntries.first().period.value shouldBe "2907"
        }

        "entries for different polling profiles are stored and read independently" {
            val skiProfile = createSkiProfile()
            repository.addOrUpdateProfile(skiProfile)

            val entryIsmar = createSkiLeaderboardEntry(profileId = "ismar", entryUserId = skiProfile.id)
            val entryMirela = createSkiLeaderboardEntry(profileId = "mirela", entryUserId = skiProfile.id)
            repository.addOrUpdateLeaderboardEntry(entryIsmar)
            repository.addOrUpdateLeaderboardEntry(entryMirela)

            val ismarEntries = repository.leaderboardEntriesByProfileAndPeriodType("ismar", PeriodType.DAY)
            val mirelaEntries = repository.leaderboardEntriesByProfileAndPeriodType("mirela", PeriodType.DAY)

            ismarEntries shouldHaveSize 1
            ismarEntries.first().profileId shouldBe "ismar"

            mirelaEntries shouldHaveSize 1
            mirelaEntries.first().profileId shouldBe "mirela"
        }
    })

private fun SkiProfileEntity.shouldBeEqualToProfile(
    profile: SkiProfile,
    expectedVersion: Short,
    shouldCreatedTimeBeNull: Boolean,
    shouldUpdatedTimeBeNull: Boolean,
) {
    id.value shouldBe profile.id
    name shouldBe profile.name
    profileImageUrl shouldBe profile.profileImageUrl
    isPrivate shouldBe profile.isPrivate

    if (shouldCreatedTimeBeNull) createdTime.shouldBeNull() else createdTime.shouldNotBeNull()
    if (shouldUpdatedTimeBeNull) updatedTime.shouldBeNull() else updatedTime.shouldNotBeNull()
    version shouldBe expectedVersion
}

private fun SkiLeaderboardEntryEntity.shouldBeEqualToEntry(
    entry: SkiLeaderboardEntry,
    expectedVersion: Short,
    shouldCreatedTimeBeNull: Boolean,
    shouldUpdatedTimeBeNull: Boolean,
) {
    id.value shouldBe entry.id
    profileId shouldBe entry.profileId
    periodType shouldBe entry.period.type.name
    periodValue shouldBe entry.period.value
    startDate shouldBe entry.period.startDate.toString()
    weekId shouldBe entry.period.weekId
    seasonId shouldBe entry.period.seasonId
    seasonName shouldBe entry.period.seasonName
    year shouldBe entry.period.year
    weekNumber shouldBe entry.period.weekNumber
    entryUserId shouldBe entry.entryUserId
    position shouldBe entry.position
    dropHeightInMeter shouldBe entry.dropHeightInMeter

    if (shouldCreatedTimeBeNull) createdTime.shouldBeNull() else createdTime.shouldNotBeNull()
    if (shouldUpdatedTimeBeNull) updatedTime.shouldBeNull() else updatedTime.shouldNotBeNull()
    version shouldBe expectedVersion
}
