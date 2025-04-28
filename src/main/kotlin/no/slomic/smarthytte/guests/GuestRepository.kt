package no.slomic.smarthytte.guests

import no.slomic.smarthytte.common.PersistenceResult

interface GuestRepository {
    suspend fun addOrUpdate(guest: Guest): PersistenceResult
    suspend fun setNotionId(notionId: String, guestId: String): PersistenceResult
}
