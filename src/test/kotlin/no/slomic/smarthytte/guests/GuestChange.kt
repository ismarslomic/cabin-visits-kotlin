package no.slomic.smarthytte.guests

/**
 * Test helper: the properties to change on a stored guest, where null means unchanged. The API and the repository
 * only take full updates, so [applyTo] fills in the rest from the stored guest. Use [GuestProperties] directly to
 * clear email or role.
 */
data class GuestChange(
    val firstName: String? = null,
    val lastName: String? = null,
    val birthYear: Short? = null,
    val gender: Gender? = null,
    val email: String? = null,
    val isFamily: Boolean? = null,
    val role: AppRole? = null,
) {
    /** A full update of [stored], or of a guest that does not exist (`null`) with the changes on top of a fixture. */
    fun applyTo(stored: Guest?): GuestProperties = (stored ?: guest).let {
        GuestProperties(
            firstName = firstName ?: it.firstName,
            lastName = lastName ?: it.lastName,
            birthYear = birthYear ?: it.birthYear,
            gender = gender ?: it.gender,
            email = email ?: it.email,
            isFamily = isFamily ?: it.isFamily,
            role = role ?: it.role,
        )
    }

    fun toRequest(stored: Guest?, version: Short) = applyTo(stored).toRequest(version)
}

fun GuestProperties.toRequest(version: Short) = GuestUpdate(
    version = version,
    firstName = firstName,
    lastName = lastName,
    birthYear = birthYear,
    gender = gender,
    email = email,
    isFamily = isFamily,
    role = role,
)
