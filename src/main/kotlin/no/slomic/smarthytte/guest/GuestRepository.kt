package no.slomic.smarthytte.guest

import no.slomic.smarthytte.common.UpsertStatus

interface GuestRepository {
    suspend fun addOrUpdate(guest: Guest): Guest
    suspend fun setNotionId(notionId: String, guestId: String): UpsertStatus
}
