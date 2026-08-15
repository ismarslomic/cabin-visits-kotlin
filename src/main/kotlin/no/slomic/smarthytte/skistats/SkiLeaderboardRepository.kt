package no.slomic.smarthytte.skistats

import no.slomic.smarthytte.common.PersistenceResult

interface SkiLeaderboardRepository {
    suspend fun addOrUpdateLeaderboardEntry(entry: SkiLeaderboardEntry): PersistenceResult
    suspend fun leaderboardEntriesByProfileAndPeriodType(
        profileId: String,
        periodType: PeriodType,
    ): List<SkiLeaderboardEntry>
}
