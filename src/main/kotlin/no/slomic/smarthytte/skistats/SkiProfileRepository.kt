package no.slomic.smarthytte.skistats

import no.slomic.smarthytte.common.PersistenceResult

interface SkiProfileRepository {
    suspend fun addOrUpdateProfile(profile: SkiProfile): PersistenceResult
}
