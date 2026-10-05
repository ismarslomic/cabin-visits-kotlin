package no.slomic.smarthytte.guests

import no.slomic.smarthytte.common.PersistenceResult
import kotlin.time.Instant

interface GuestRepository {
    suspend fun allGuests(): List<Guest>
    suspend fun guestById(id: String): Guest?

    /**
     * Adds or updates the [guest] with its [guestAvatarImage]. A null [guestAvatarImage] means the guest has no avatar.
     */
    suspend fun addOrUpdate(guest: Guest, guestAvatarImage: ByteArray? = null): PersistenceResult
    suspend fun setNotionId(notionId: String, guestId: String): PersistenceResult
    suspend fun avatarById(guestId: String): GuestAvatar?

    /**
     * Returns the avatar's last updated time by guest id, only for guests that have an avatar.
     *
     * Used to build `avatarUrl` in the live stats: a guest missing from the map has no avatar (null url), and the
     * updated time is the version in the url (`?v=`), so the url changes when the image does. Reads only these two
     * columns, so unlike [avatarById] it does not load the image bytes.
     */
    suspend fun allAvatarUpdatedTimes(): Map<String, Instant>
}
