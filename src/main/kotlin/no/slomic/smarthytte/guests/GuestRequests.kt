package no.slomic.smarthytte.guests

import kotlinx.serialization.Serializable

/**
 * The request body of `PUT /api/guests/{guestId}`: a full replace. The [version] the client read and every writable
 * property are required; `null` for [email] or [role] clears it (no role means no access). Properties of the guest
 * that are not writable, such as `id` or `avatarUrl`, are ignored.
 */
@Serializable
data class GuestUpdate(
    val version: Short,
    val firstName: String,
    val lastName: String,
    val birthYear: Short,
    val gender: Gender,
    val email: String?,
    val isFamily: Boolean,
    val role: AppRole?,
) {
    fun toProperties() = GuestProperties(
        firstName = firstName,
        lastName = lastName,
        birthYear = birthYear,
        gender = gender,
        email = email,
        isFamily = isFamily,
        role = role,
    )
}

/**
 * The request body of `POST /api/guests`. The id is generated from the name. A guest without [role] has no access, and
 * one without [email] cannot log in.
 */
@Serializable
data class GuestCreate(
    val firstName: String,
    val lastName: String,
    val birthYear: Short,
    val gender: Gender,
    val email: String? = null,
    val isFamily: Boolean = false,
    val role: AppRole? = null,
) {
    fun toProperties() = GuestProperties(
        firstName = firstName,
        lastName = lastName,
        birthYear = birthYear,
        gender = gender,
        email = email,
        isFamily = isFamily,
        role = role,
    )
}
