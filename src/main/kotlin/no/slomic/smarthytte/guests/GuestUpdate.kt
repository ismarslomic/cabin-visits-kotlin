package no.slomic.smarthytte.guests

import kotlinx.serialization.Serializable
import no.slomic.smarthytte.auth.AppRole

/** A partial update of a guest: properties that are null are left unchanged. */
@Serializable
data class GuestUpdate(
    val firstName: String? = null,
    val lastName: String? = null,
    val birthYear: Short? = null,
    val gender: Gender? = null,
    val email: String? = null,
    val isFamily: Boolean? = null,
    val appRole: AppRole? = null,
) {
    /** Only an admin may change email, isFamily and appRole. */
    fun requiresAdmin(): Boolean = email != null || isFamily != null || appRole != null
}

/** The guest properties that can be changed through the API, and that the guests.json sync then leaves alone. */
enum class GuestField {
    FIRST_NAME,
    LAST_NAME,
    BIRTH_YEAR,
    GENDER,
    EMAIL,
    IS_FAMILY,
    APP_ROLE,
}
