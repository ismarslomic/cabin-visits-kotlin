package no.slomic.smarthytte.skistats

import no.slomic.smarthytte.common.PersistenceResult

interface SkiStatsRepository {
    suspend fun tokensByProfile(id: String): SkiStatsTokens?
    suspend fun addOrUpdateTokens(id: String, tokens: SkiStatsTokens): PersistenceResult
}
