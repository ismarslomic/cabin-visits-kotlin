package no.slomic.smarthytte.guests

import no.slomic.smarthytte.auth.UserPrincipal
import no.slomic.smarthytte.common.toUtcDate
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import kotlin.time.Clock

private const val MAX_NAME_LENGTH = 20
private const val MAX_EMAIL_LENGTH = 255
private const val MIN_BIRTH_YEAR = 1900
private const val UNIQUE_EMAIL_INDEX = "uq_guest_email"
private const val EMAIL_IN_USE = "The email is already used by another user"
private val EMAIL_FORMAT = Regex("^[^@\\s]+@[^@\\s]+$")

sealed interface GuestUpdateResult {
    data class Updated(val guest: Guest) : GuestUpdateResult
    data object NotFound : GuestUpdateResult
    data class Forbidden(val message: String) : GuestUpdateResult
    data class Conflict(val message: String) : GuestUpdateResult
    data class Invalid(val message: String) : GuestUpdateResult
}

/**
 * Changes the properties of guests.
 *
 * Every user may change their own firstName, lastName, birthYear and gender ([updateOwnProfile]). An
 * [AppRole.ADMIN] may change all properties of every guest ([update]), but not remove their own admin role or change
 * their own email there. An email can only belong to one guest, also in the database: sharing it makes the identity
 * ambiguous and denies all of them.
 */
class GuestProfileService(private val guestRepository: GuestRepository) {
    suspend fun update(actor: UserPrincipal, guestId: String, update: GuestUpdate): GuestUpdateResult =
        forbiddenReason(actor, guestId, update)?.let(GuestUpdateResult::Forbidden)
            ?: validateAndSave(actor.id, guestId, update)

    /** Changes the properties of the [user]'s own guest. The email cannot be changed, since it identifies the user. */
    suspend fun updateOwnProfile(user: UserPrincipal, update: MeUpdate): GuestUpdateResult =
        validateAndSave(user.id, user.id, update.toGuestUpdate())

    private suspend fun validateAndSave(actorId: String, guestId: String, update: GuestUpdate): GuestUpdateResult =
        invalidReason(update)?.let(GuestUpdateResult::Invalid)
            ?: guestRepository.guestById(guestId)?.let { save(actorId, it, update.trimmed()) }
            ?: GuestUpdateResult.NotFound

    private suspend fun save(actorId: String, stored: Guest, update: GuestUpdate): GuestUpdateResult =
        conflictReason(stored, update)?.let(GuestUpdateResult::Conflict) ?: persist(actorId, stored.id, update)

    /** The database has the last word on a unique email, so two concurrent updates cannot both succeed. */
    private suspend fun persist(actorId: String, guestId: String, update: GuestUpdate): GuestUpdateResult = try {
        guestRepository.update(guestId, update, actorId)
        guestRepository.guestById(guestId)?.let(GuestUpdateResult::Updated) ?: GuestUpdateResult.NotFound
    } catch (e: ExposedSQLException) {
        if (e.isDuplicateEmail()) GuestUpdateResult.Conflict(EMAIL_IN_USE) else throw e
    }

    /** A changed email must not be used by another guest. */
    private suspend fun conflictReason(stored: Guest, update: GuestUpdate): String? {
        val email = update.email?.takeUnless { it.equals(stored.email, ignoreCase = true) } ?: return null

        val isUsedByOtherGuest = guestRepository.guestsByEmail(email).any { it.id != stored.id }
        return EMAIL_IN_USE.takeIf { isUsedByOtherGuest }
    }

    private fun forbiddenReason(actor: UserPrincipal, guestId: String, update: GuestUpdate): String? = when {
        actor.role == AppRole.ADMIN && guestId == actor.id -> when {
            update.role != null && update.role != AppRole.ADMIN -> "An admin cannot remove their own admin role"
            update.email.changesEmailOf(actor) -> "An admin cannot change their own email"
            else -> null
        }

        actor.role == AppRole.ADMIN -> null

        guestId != actor.id -> "Only an admin can change other guests"

        update.requiresAdmin() -> "Only an admin can change email, isFamily and role"

        else -> null
    }

    private fun invalidReason(update: GuestUpdate): String? = listOfNotNull(
        "firstName must be 1-$MAX_NAME_LENGTH characters".takeIf { update.firstName.isInvalidName() },
        "lastName must be 1-$MAX_NAME_LENGTH characters".takeIf { update.lastName.isInvalidName() },
        "birthYear must be between $MIN_BIRTH_YEAR and the current year".takeIf { update.birthYear.isInvalidYear() },
        "email must be a valid address of at most $MAX_EMAIL_LENGTH characters".takeIf {
            update.email.isInvalidEmail()
        },
    ).firstOrNull()
}

private fun GuestUpdate.trimmed() =
    copy(firstName = firstName?.trim(), lastName = lastName?.trim(), email = email?.trim())

private fun ExposedSQLException.isDuplicateEmail() =
    generateSequence<Throwable>(this) { it.cause }.any { it.message?.contains(UNIQUE_EMAIL_INDEX) == true }

private fun String?.changesEmailOf(actor: UserPrincipal) =
    this != null && !trim().equals(actor.email, ignoreCase = true)

private fun String?.isInvalidName() = this != null && trim().length !in 1..MAX_NAME_LENGTH

private fun Short?.isInvalidYear() = this != null && toInt() !in MIN_BIRTH_YEAR..Clock.System.now().toUtcDate().year

private fun String?.isInvalidEmail() = this != null && (length > MAX_EMAIL_LENGTH || !EMAIL_FORMAT.matches(trim()))
