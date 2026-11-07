package no.slomic.smarthytte.guests

import kotlinx.serialization.Serializable

/** A partial update of a guest: properties that are null are left unchanged. */
@Serializable
data class GuestUpdate(
    val firstName: String? = null,
    val lastName: String? = null,
    val birthYear: Short? = null,
    val gender: Gender? = null,
    val email: String? = null,
    val isFamily: Boolean? = null,
    val role: AppRole? = null,
) {
    /** Only an admin may change email, isFamily and role. */
    fun requiresAdmin(): Boolean = email != null || isFamily != null || role != null
}
