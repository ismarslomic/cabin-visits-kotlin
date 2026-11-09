package no.slomic.smarthytte.properties

data class BootstrapAdminPropertiesHolder(val bootstrapAdmin: BootstrapAdminProperties)

/**
 * The first admin of an empty database, see [no.slomic.smarthytte.guests.BootstrapAdmin]. All are optional and blank
 * means not set. They are bound as strings because the configuration library fails on an empty value for a number or an
 * enum: the birth year and the gender are parsed by the bootstrap.
 *
 * @property email The email the admin logs in with.
 * @property firstName First name, 1-20 characters.
 * @property lastName Last name, 1-20 characters.
 * @property birthYear Year of birth.
 * @property gender `MALE` or `FEMALE`.
 */
data class BootstrapAdminProperties(
    val email: String,
    val firstName: String,
    val lastName: String,
    val birthYear: String,
    val gender: String,
)
