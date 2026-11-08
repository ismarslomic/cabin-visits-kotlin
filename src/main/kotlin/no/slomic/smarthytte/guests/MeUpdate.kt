package no.slomic.smarthytte.guests

import kotlinx.serialization.Serializable

/**
 * The request body of `PUT /api/me`: a full replace of the properties a user can change on their own profile. All are
 * required, together with the [version] the client read. Other properties, such as email, isFamily and role, are
 * ignored: they stay as they are, since the user is identified by the email and has to be given access to a new one in
 * Cloudflare Access.
 */
@Serializable
data class MeUpdate(
    val version: Short,
    val firstName: String,
    val lastName: String,
    val birthYear: Short,
    val gender: Gender,
) {
    /** The values of the guest with the four profile properties replaced and every other property unchanged. */
    fun applyTo(stored: Guest) = GuestProperties(
        firstName = firstName.trim(),
        lastName = lastName.trim(),
        birthYear = birthYear,
        gender = gender,
        email = stored.email,
        isFamily = stored.isFamily,
        role = stored.role,
    )
}
