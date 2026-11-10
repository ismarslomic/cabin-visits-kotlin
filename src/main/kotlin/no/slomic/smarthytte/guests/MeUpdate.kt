package no.slomic.smarthytte.guests

import io.ktor.openapi.JsonSchema
import kotlinx.serialization.Serializable

/**
 * The request body of `PUT /api/me`: a full replace of the properties a user can change on their own profile. All are
 * required, together with the [version] the client read. Other properties, such as email, isFamily and role, are
 * ignored: they stay as they are, since the user is identified by the email and has to be given access to a new one in
 * Cloudflare Access.
 */
@Serializable
data class MeUpdate(
    @JsonSchema.Description(
        "The version of the guest that the client read, from GET /api/me. An outdated version gives 412.",
    )
    @JsonSchema.Example("3")
    val version: Short,
    @JsonSchema.Description("The first name, 1-20 characters. Leading and trailing spaces are removed.")
    @JsonSchema.Example("\"Ola\"")
    val firstName: String,
    @JsonSchema.Description("The last name, 1-20 characters. Leading and trailing spaces are removed.")
    @JsonSchema.Example("\"Nordmann\"")
    val lastName: String,
    @JsonSchema.Description("The year of birth.")
    @JsonSchema.Example("1985")
    val birthYear: Short,
    @JsonSchema.Description("The gender: MALE or FEMALE.")
    @JsonSchema.Example("\"MALE\"")
    val gender: Gender,
) {
    /** The values of the guest with the four profile properties replaced and every other property unchanged. */
    fun applyTo(stored: Guest) = stored.toProperties().copy(
        firstName = firstName.trim(),
        lastName = lastName.trim(),
        birthYear = birthYear,
        gender = gender,
    )
}
