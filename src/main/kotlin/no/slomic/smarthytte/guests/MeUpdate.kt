package no.slomic.smarthytte.guests

import kotlinx.serialization.Serializable

/**
 * The properties a user can change on their own profile, see `POST /api/me`. All are required. The email cannot be
 * changed here, since the user is identified by it and has to be given access to the new email in Cloudflare Access.
 */
@Serializable
data class MeUpdate(val firstName: String, val lastName: String, val birthYear: Short, val gender: Gender) {
    fun toGuestUpdate() = GuestUpdate(
        firstName = firstName,
        lastName = lastName,
        birthYear = birthYear,
        gender = gender,
    )
}
