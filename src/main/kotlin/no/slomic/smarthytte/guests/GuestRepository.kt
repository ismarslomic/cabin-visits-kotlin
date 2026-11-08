package no.slomic.smarthytte.guests

import no.slomic.smarthytte.common.PersistenceResult
import no.slomic.smarthytte.common.SYSTEM_ACTOR
import kotlin.time.Instant

/**
 * What [GuestRepository.update] did, or why it refused to.
 *
 * Not a [PersistenceResult], which only says what a write did to the data (added, updated, deleted or nothing) and is
 * counted for imports and syncs, where nothing can be refused. `update` is a conditional write from the API, and the
 * caller answers each case differently: [Updated] and [Unchanged] are 200, [NotFound] is 404 and [VersionMismatch] is
 * 412. `PersistenceResult.NO_ACTION` would hide which of the last three happened. The version has to be compared in
 * the same transaction as the write, so only the repository can tell [VersionMismatch] from the rest, and a service
 * that read the guest first would race.
 *
 * Reservations will get an outcome of their own, since they can also be refused for an overlap.
 */
sealed interface GuestUpdateOutcome {
    /** The guest changed: `version` was bumped. */
    data object Updated : GuestUpdateOutcome

    /** The guest exists and has the expected version, but nothing in the update differs from what is stored. */
    data object Unchanged : GuestUpdateOutcome
    data object NotFound : GuestUpdateOutcome

    /** The stored version is not the one the client read. Nothing was changed. */
    data object VersionMismatch : GuestUpdateOutcome
}

interface GuestRepository {
    suspend fun allGuests(): List<Guest>
    suspend fun guestById(id: String): Guest?

    /**
     * Adds or updates the [guest]: every property of the stored guest is overwritten. Used as a fixture/import upsert,
     * the API uses [create] and [update]. The avatar is not touched, see [saveAvatar]. The [actor] goes in `created_by`
     * (or `updated_by` for a change).
     */
    suspend fun addOrUpdate(guest: Guest, actor: String = SYSTEM_ACTOR): PersistenceResult

    /** Returns the guests with the [email], ignoring case and surrounding whitespace. */
    suspend fun guestsByEmail(email: String): List<Guest>

    /**
     * Replaces the writable properties of the guest with [update], if the stored version is [expectedVersion]. The
     * version is checked in the same transaction as the write. A change bumps `version` and sets `updated_time` and
     * `updated_by` ([actor]).
     */
    suspend fun update(
        guestId: String,
        expectedVersion: Short,
        update: GuestProperties,
        actor: String,
    ): GuestUpdateOutcome

    /**
     * Creates a guest with an id generated from the name (`ola.nordmann`, `ola.nordmann-2` on a collision), in one
     * transaction so it is safe to re-run. Sets `created_*` ([actor]). A shared email violates the unique index.
     */
    suspend fun create(guest: GuestProperties, actor: String): Guest

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
