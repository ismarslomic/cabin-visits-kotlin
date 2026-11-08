package no.slomic.smarthytte.guests

import org.jetbrains.exposed.v1.exceptions.ExposedSQLException

sealed interface GuestCreateResult {
    data class Created(val guest: Guest) : GuestCreateResult
    data class Invalid(val message: String) : GuestCreateResult
    data object EmailInUse : GuestCreateResult
}

/**
 * Creates guests for the admin. The id is generated from the name by the repository, inside the same transaction as
 * the insert. A guest without a role has no access, and one with a role but no email cannot log in; a guest that
 * has to log in also needs an entry in the Cloudflare Access policy.
 */
class GuestAdminService(private val guestRepository: GuestRepository) {
    suspend fun create(request: GuestCreate, actorId: String): GuestCreateResult {
        val guest = request.toProperties().let {
            it.copy(firstName = it.firstName.trim(), lastName = it.lastName.trim(), email = normalizedEmail(it.email))
        }

        return invalidGuestReason(guest.firstName, guest.lastName, guest.birthYear, guest.email)
            ?.let(GuestCreateResult::Invalid)
            ?: save(guest, actorId)
    }

    private suspend fun save(guest: GuestProperties, actorId: String): GuestCreateResult {
        val isUsed = guest.email?.let { guestRepository.guestsByEmail(it).isNotEmpty() } == true

        return if (isUsed) GuestCreateResult.EmailInUse else persist(guest, actorId)
    }

    /** The database has the last word on a unique email, so two concurrent creates cannot both succeed. */
    private suspend fun persist(guest: GuestProperties, actorId: String): GuestCreateResult = try {
        GuestCreateResult.Created(guestRepository.create(guest, actorId))
    } catch (e: ExposedSQLException) {
        if (e.isDuplicateEmail()) GuestCreateResult.EmailInUse else throw e
    }
}
