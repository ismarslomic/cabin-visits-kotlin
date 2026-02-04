package no.slomic.smarthytte.skistats

import no.slomic.smarthytte.common.PersistenceResult

interface SkiStatsRepository {
    suspend fun tokensByProfile(userProfileId: String): SkiStatsTokens?
    suspend fun addOrUpdateTokens(userProfileId: String, tokens: SkiStatsTokens): PersistenceResult
}
