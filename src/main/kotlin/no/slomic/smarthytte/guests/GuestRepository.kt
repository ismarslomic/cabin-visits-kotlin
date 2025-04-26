package no.slomic.smarthytte.guests

import no.slomic.smarthytte.common.UpsertStatus

interface GuestRepository {
    suspend fun addOrUpdate(guest: Guest): UpsertStatus
    suspend fun setNotionId(notionId: String, guestId: String): UpsertStatus
}
