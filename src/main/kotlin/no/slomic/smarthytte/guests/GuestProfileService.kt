package no.slomic.smarthytte.guests

import no.slomic.smarthytte.auth.AppUser
import no.slomic.smarthytte.common.toUtcDate
import kotlin.time.Clock

private const val MAX_NAME_LENGTH = 20
private const val MAX_EMAIL_LENGTH = 255
private const val MIN_BIRTH_YEAR = 1900
private val EMAIL_FORMAT = Regex("^[^@\\s]+@[^@\\s]+$")

sealed interface GuestUpdateResult {
    data class Updated(val guest: Guest) : GuestUpdateResult
    data object NotFound : GuestUpdateResult
    data class Forbidden(val message: String) : GuestUpdateResult
    data class Conflict(val message: String) : GuestUpdateResult
    data class Invalid(val message: String) : GuestUpdateResult
}

/**
 * Decides who may change which properties of a guest.
 *
 * Every role may change firstName, lastName, birthYear and gender of their own guest. An [AppRole.ADMIN] may change all
 * properties of every guest, but not remove their own admin role or change their own email, since the email is how the
 * user is identified. An email can only be used by one guest with a role: sharing it makes the identity ambiguous and
 * denies all of them.
 */
class GuestProfileService(private val guestRepository: GuestRepository) {
    suspend fun update(actor: AppUser, guestId: String, update: GuestUpdate): GuestUpdateResult {
        val rejection: GuestUpdateResult? = forbiddenReason(actor, guestId, update)?.let(GuestUpdateResult::Forbidden)
            ?: invalidReason(update)?.let(GuestUpdateResult::Invalid)

        return rejection ?: run {
            val stored = guestRepository.guestById(guestId) ?: return GuestUpdateResult.NotFound
            val trimmed = update.trimmed()

            conflictReason(stored, trimmed)?.let(GuestUpdateResult::Conflict) ?: run {
                guestRepository.update(guestId, trimmed)
                guestRepository.guestById(guestId)?.let(GuestUpdateResult::Updated) ?: GuestUpdateResult.NotFound
            }
        }
    }

    /** The email of the guest after the update must not be used by another guest with a role. */
    private suspend fun conflictReason(stored: Guest, update: GuestUpdate): String? {
        val email = update.email ?: stored.email
        val appRole = update.appRole ?: stored.appRole
        if (email == null || appRole == null) return null

        val isUsedByOtherUser = guestRepository.guestsByEmail(email).any { it.id != stored.id && it.appRole != null }
        return "The email is already used by another user".takeIf { isUsedByOtherUser }
    }

    private fun forbiddenReason(actor: AppUser, guestId: String, update: GuestUpdate): String? = when {
        actor.role == AppRole.ADMIN && guestId == actor.id -> when {
            update.appRole != null && update.appRole != AppRole.ADMIN -> "An admin cannot remove their own admin role"
            update.email.changesEmailOf(actor) -> "An admin cannot change their own email"
            else -> null
        }

        actor.role == AppRole.ADMIN -> null

        guestId != actor.id -> "Only an admin can change other guests"

        update.requiresAdmin() -> "Only an admin can change email, isFamily and appRole"

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

    private fun GuestUpdate.trimmed() =
        copy(firstName = firstName?.trim(), lastName = lastName?.trim(), email = email?.trim())

    private fun String?.changesEmailOf(actor: AppUser) = this != null && !trim().equals(actor.email, ignoreCase = true)

    private fun String?.isInvalidName() = this != null && trim().length !in 1..MAX_NAME_LENGTH

    private fun Short?.isInvalidYear() = this != null && toInt() !in MIN_BIRTH_YEAR..Clock.System.now().toUtcDate().year

    private fun String?.isInvalidEmail() = this != null && (length > MAX_EMAIL_LENGTH || !EMAIL_FORMAT.matches(trim()))
}
