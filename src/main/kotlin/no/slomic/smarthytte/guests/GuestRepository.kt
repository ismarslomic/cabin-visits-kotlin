package no.slomic.smarthytte.guests

import no.slomic.smarthytte.common.PersistenceResult

interface GuestRepository {
    suspend fun allGuests(): List<Guest>
    suspend fun guestById(id: String): Guest?

    /**
     * Adds or updates the [guest] with its [guestAvatarImage]. A null [guestAvatarImage] means the guest has no avatar.
     */
    suspend fun addOrUpdate(guest: Guest, guestAvatarImage: ByteArray? = null): PersistenceResult
    suspend fun setNotionId(notionId: String, guestId: String): PersistenceResult
    suspend fun avatarImageById(guestId: String): ByteArray?
}
