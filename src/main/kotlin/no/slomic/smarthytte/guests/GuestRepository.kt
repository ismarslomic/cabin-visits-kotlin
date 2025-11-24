package no.slomic.smarthytte.guests

import no.slomic.smarthytte.common.PersistenceResult

interface GuestRepository {
    suspend fun allGuests(): List<Guest>
    suspend fun guestById(id: String): Guest?
    suspend fun addOrUpdate(guest: Guest): PersistenceResult
    suspend fun setNotionId(notionId: String, guestId: String): PersistenceResult
}
