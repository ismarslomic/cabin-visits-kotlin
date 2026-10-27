package no.slomic.smarthytte.guests

import no.slomic.smarthytte.auth.AppRole
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
    data class Invalid(val message: String) : GuestUpdateResult
}

/**
 * Decides who may change which properties of a guest.
 *
 * Every role may change firstName, lastName, birthYear and gender of their own guest. An [AppRole.ADMIN] may change all
 * properties of every guest, but not remove their own admin role.
 */
class GuestProfileService(private val guestRepository: GuestRepository) {
    suspend fun update(actor: AppUser, guestId: String, update: GuestUpdate): GuestUpdateResult {
        val rejection: GuestUpdateResult? = forbiddenReason(actor, guestId, update)?.let(GuestUpdateResult::Forbidden)
            ?: invalidReason(update)?.let(GuestUpdateResult::Invalid)

        return rejection ?: run {
            guestRepository.update(guestId, update.trimmed())
            guestRepository.guestById(guestId)?.let(GuestUpdateResult::Updated) ?: GuestUpdateResult.NotFound
        }
    }

    private fun forbiddenReason(actor: AppUser, guestId: String, update: GuestUpdate): String? = when {
        actor.role == AppRole.ADMIN ->
            "An admin cannot remove their own admin role"
                .takeIf { guestId == actor.id && update.appRole != null && update.appRole != AppRole.ADMIN }

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

    private fun String?.isInvalidName() = this != null && trim().length !in 1..MAX_NAME_LENGTH

    private fun Short?.isInvalidYear() = this != null && toInt() !in MIN_BIRTH_YEAR..Clock.System.now().toUtcDate().year

    private fun String?.isInvalidEmail() = this != null && (length > MAX_EMAIL_LENGTH || !EMAIL_FORMAT.matches(trim()))
}
