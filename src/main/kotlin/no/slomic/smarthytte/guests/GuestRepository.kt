package no.slomic.smarthytte.guests

import no.slomic.smarthytte.common.PersistenceResult
import no.slomic.smarthytte.common.SYSTEM_ACTOR
import kotlin.time.Instant

interface GuestRepository {
    suspend fun allGuests(): List<Guest>
    suspend fun guestById(id: String): Guest?

    /**
     * Adds or updates the [guest] with its [guestAvatarImage]: every property of the stored guest is overwritten.
     * A null [guestAvatarImage] means the guest has no avatar. Used as a fixture/import upsert, the API uses [update].
     * The [actor] goes in `created_by` (or `updated_by` for a change).
     */
    suspend fun addOrUpdate(
        guest: Guest,
        guestAvatarImage: ByteArray? = null,
        actor: String = SYSTEM_ACTOR,
    ): PersistenceResult

    /** Returns the guests with the [email], ignoring case and surrounding whitespace. */
    suspend fun guestsByEmail(email: String): List<Guest>

    /**
     * Changes the properties of the guest that are set in [update]. Returns [PersistenceResult.NO_ACTION] if the guest
     * is unknown or nothing changed. A change bumps `version` and sets `updated_time` and `updated_by` ([actor]).
     */
    suspend fun update(guestId: String, update: GuestUpdate, actor: String): PersistenceResult

    /**
     * Creates the avatar of the guest, or replaces it if the image differs: [PersistenceResult.ADDED] (`created_*`),
     * [PersistenceResult.UPDATED] (`updated_*`) or [PersistenceResult.NO_ACTION] for the same image. A guest's own
     * `version` and `updated_*` are not touched. Does nothing if the guest is unknown.
     */
    suspend fun saveAvatar(guestId: String, image: ByteArray, actor: String): PersistenceResult
    suspend fun avatarById(guestId: String): GuestAvatar?

    /**
     * Returns the avatar's version time (`updated_time`, or `created_time` when never replaced) by guest id, only for
     * guests that have an avatar.
     *
     * Used to build `avatarUrl` in the live stats: a guest missing from the map has no avatar (null url), and the
     * version time is the version in the url (`?v=`), so the url changes when the image does. Reads only these
     * columns, so unlike [avatarById] it does not load the image bytes.
     */
    suspend fun allAvatarVersionTimes(): Map<String, Instant>
}
