package no.slomic.smarthytte.guests

import no.slomic.smarthytte.auth.UserPrincipal
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException

private const val UNIQUE_EMAIL_INDEX = "uq_guest_email"
internal const val EMAIL_IN_USE_MESSAGE = "The email is already used by another user"
internal const val LAST_ADMIN_MESSAGE = "There must be at least one admin with an email"
private const val VERSION_MESSAGE = "The guest has been changed since it was read"

sealed interface GuestUpdateResult {
    data class Updated(val guest: Guest) : GuestUpdateResult
    data object NotFound : GuestUpdateResult
    data class Forbidden(val message: String) : GuestUpdateResult
    data class Conflict(val message: String) : GuestUpdateResult
    data class Invalid(val message: String) : GuestUpdateResult

    /** The version in the request is not the stored one (412). */
    data class VersionMismatch(val message: String = VERSION_MESSAGE) : GuestUpdateResult
}

/**
 * Replaces the writable properties of guests (full updates, `PUT`).
 *
 * An [AppRole.ADMIN] replaces every property of every guest ([update]), but cannot remove their own admin role or
 * change their own email there, and never leaves the application without an admin that has an email. Every user
 * replaces their own firstName, lastName, birthYear and gender ([updateOwnProfile]); the other properties stay as they
 * are. An email can only belong to one guest, also in the database: sharing it makes the identity ambiguous and denies
 * all of them.
 *
 * Every update carries the version the client read. Order of the checks: forbidden (403), invalid (400), unknown guest
 * (404), stale version (412), last admin (409), email in use (409).
 */
class GuestProfileService(private val guestRepository: GuestRepository) {
    suspend fun update(actor: UserPrincipal, guestId: String, request: GuestUpdate): GuestUpdateResult {
        val update = request.toProperties().normalized()

        return forbiddenReason(actor, guestId, update)?.let(GuestUpdateResult::Forbidden)
            ?: invalidReason(update)?.let(GuestUpdateResult::Invalid)
            ?: guestRepository.guestById(guestId)?.let { save(actor.id, it, request.version, update) }
            ?: GuestUpdateResult.NotFound
    }

    /** Replaces the profile of the [user]'s own guest. The email cannot be changed, since it identifies the user. */
    suspend fun updateOwnProfile(user: UserPrincipal, request: MeUpdate): GuestUpdateResult =
        guestRepository.guestById(user.id)?.let { stored ->
            val update = request.applyTo(stored)

            invalidReason(update)?.let(GuestUpdateResult::Invalid) ?: save(user.id, stored, request.version, update)
        } ?: GuestUpdateResult.NotFound

    private suspend fun save(
        actorId: String,
        stored: Guest,
        version: Short,
        update: GuestProperties,
    ): GuestUpdateResult = when {
        stored.version != version -> GuestUpdateResult.VersionMismatch()

        else -> conflictReason(stored, update)?.let(GuestUpdateResult::Conflict)
            ?: persist(actorId, stored.id, version, update)
    }

    /**
     * The database has the last word on the version and on a unique email, so two concurrent updates cannot both
     * succeed.
     */
    private suspend fun persist(actorId: String, guestId: String, version: Short, update: GuestProperties) = try {
        when (guestRepository.update(guestId, version, update, actorId)) {
            GuestUpdateOutcome.NotFound -> GuestUpdateResult.NotFound

            GuestUpdateOutcome.VersionMismatch -> GuestUpdateResult.VersionMismatch()

            GuestUpdateOutcome.Updated, GuestUpdateOutcome.Unchanged ->
                guestRepository.guestById(guestId)?.let(GuestUpdateResult::Updated) ?: GuestUpdateResult.NotFound
        }
    } catch (e: ExposedSQLException) {
        if (e.isDuplicateEmail()) GuestUpdateResult.Conflict(EMAIL_IN_USE_MESSAGE) else throw e
    }

    /** A removed admin role or email must leave another admin with an email, and a changed email must be free. */
    private suspend fun conflictReason(stored: Guest, update: GuestProperties): String? =
        lastAdminReason(stored, update) ?: emailInUseReason(stored, update)

    private suspend fun lastAdminReason(stored: Guest, update: GuestProperties): String? {
        val staysAdminWithEmail = update.role == AppRole.ADMIN && update.email != null
        if (!stored.isAdminWithEmail() || staysAdminWithEmail) return null

        val hasOtherAdmin = guestRepository.allGuests().any { it.id != stored.id && it.isAdminWithEmail() }
        return LAST_ADMIN_MESSAGE.takeUnless { hasOtherAdmin }
    }

    private suspend fun emailInUseReason(stored: Guest, update: GuestProperties): String? {
        val email = update.email?.takeUnless { it.equals(stored.email, ignoreCase = true) } ?: return null

        val isUsedByOtherGuest = guestRepository.guestsByEmail(email).any { it.id != stored.id }
        return EMAIL_IN_USE_MESSAGE.takeIf { isUsedByOtherGuest }
    }

    /** Only an admin replaces guests (the route requires it); a user replaces their own profile through the other. */
    private fun forbiddenReason(actor: UserPrincipal, guestId: String, update: GuestProperties): String? = when {
        actor.role != AppRole.ADMIN -> "Only an admin can change guests"
        guestId != actor.id -> null
        update.role != AppRole.ADMIN -> "An admin cannot remove their own admin role"
        !update.email.equals(actor.email, ignoreCase = true) -> "An admin cannot change their own email"
        else -> null
    }

    private fun invalidReason(update: GuestProperties): String? = invalidGuestReason(
        firstName = update.firstName,
        lastName = update.lastName,
        birthYear = update.birthYear,
        email = update.email,
    )
}

internal fun Guest.isAdminWithEmail() = role == AppRole.ADMIN && email != null

/** Names trimmed and the email normalised ([normalizedEmail]): a blank email clears it. */
private fun GuestProperties.normalized() =
    copy(firstName = firstName.trim(), lastName = lastName.trim(), email = normalizedEmail(email))

internal fun ExposedSQLException.isDuplicateEmail() =
    generateSequence<Throwable>(this) { it.cause }.any { it.message?.contains(UNIQUE_EMAIL_INDEX) == true }
