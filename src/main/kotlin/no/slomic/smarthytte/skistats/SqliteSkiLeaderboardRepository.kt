package no.slomic.smarthytte.skistats

import io.ktor.util.logging.KtorSimpleLogger
import io.ktor.util.logging.Logger
import no.slomic.smarthytte.common.PersistenceResult
import no.slomic.smarthytte.common.suspendTransaction
import no.slomic.smarthytte.common.truncatedToMillis
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.eq
import kotlin.time.Clock

class SqliteSkiLeaderboardRepository : SkiLeaderboardRepository {
    private val logger: Logger = KtorSimpleLogger(SqliteSkiLeaderboardRepository::class.java.name)

    override suspend fun addOrUpdateLeaderboardEntry(entry: SkiLeaderboardEntry): PersistenceResult =
        suspendTransaction {
            val entityId: EntityID<String> = EntityID(id = entry.id, table = SkiLeaderboardEntryTable)
            val stored: SkiLeaderboardEntryEntity? = SkiLeaderboardEntryEntity.findById(entityId)

            if (stored == null) {
                addLeaderboardEntry(entry)
            } else {
                updateLeaderboardEntry(entry)
            }
        }

    private fun addLeaderboardEntry(entry: SkiLeaderboardEntry): PersistenceResult {
        logger.trace("Adding leaderboard entry with id: ${entry.id}")

        SkiLeaderboardEntryEntity.new(entry.id) {
            profileId = entry.profileId
            periodType = entry.periodType.name
            periodValue = entry.periodValue
            startDate = entry.startDate.toString()
            weekId = entry.weekId
            seasonId = entry.seasonId
            leaderboardUpdatedAtUtc = entry.leaderboardUpdatedAtUtc
            entryUserId = entry.entryUserId
            position = entry.position
            dropHeightInMeter = entry.dropHeightInMeter
            createdTime = Clock.System.now().truncatedToMillis()
        }

        logger.trace("Added leaderboard entry with id: ${entry.id}")
        return PersistenceResult.ADDED
    }

    private fun updateLeaderboardEntry(entry: SkiLeaderboardEntry): PersistenceResult {
        logger.trace("Updating leaderboard entry with id: ${entry.id}")

        val stored: SkiLeaderboardEntryEntity =
            SkiLeaderboardEntryEntity.findById(entry.id) ?: return PersistenceResult.NO_ACTION

        with(stored) {
            leaderboardUpdatedAtUtc = entry.leaderboardUpdatedAtUtc
            position = entry.position
            dropHeightInMeter = entry.dropHeightInMeter
        }

        val isDirty: Boolean = stored.writeValues.isNotEmpty()

        return if (isDirty) {
            stored.version = stored.version.inc()
            stored.updatedTime = Clock.System.now().truncatedToMillis()

            logger.trace("Updated leaderboard entry with id: ${entry.id}")
            PersistenceResult.UPDATED
        } else {
            logger.trace("No changes detected for leaderboard entry with id: ${entry.id}")
            PersistenceResult.NO_ACTION
        }
    }

    override suspend fun leaderboardEntriesByProfileAndPeriodType(
        profileId: String,
        periodType: PeriodType,
    ): List<SkiLeaderboardEntry> = suspendTransaction {
        SkiLeaderboardEntryEntity.find {
            (SkiLeaderboardEntryTable.profileId eq profileId) and
                (SkiLeaderboardEntryTable.periodType eq periodType.name)
        }.map(::daoToModel)
    }
}
