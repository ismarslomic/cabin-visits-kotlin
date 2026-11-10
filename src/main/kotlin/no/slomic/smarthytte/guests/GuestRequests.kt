package no.slomic.smarthytte.guests

import io.ktor.openapi.JsonSchema
import kotlinx.serialization.Serializable

/**
 * The request body of `PUT /api/guests/{guestId}`: a full replace. The [version] the client read and every writable
 * property are required; `null` for [email] or [role] clears it (no role means no access). Properties of the guest
 * that are not writable, such as `id` or `avatarUrl`, are ignored.
 */
@Serializable
data class GuestUpdate(
    @JsonSchema.Description(
        "The version of the guest that the client read, from GET /api/guests. An outdated version gives 412.",
    )
    @JsonSchema.Example("3")
    val version: Short,
    @JsonSchema.Description("The first name, 1-20 characters.")
    @JsonSchema.Example("\"Ola\"")
    val firstName: String,
    @JsonSchema.Description("The last name, 1-20 characters.")
    @JsonSchema.Example("\"Nordmann\"")
    val lastName: String,
    @JsonSchema.Description("The year of birth.")
    @JsonSchema.Example("1985")
    val birthYear: Short,
    @JsonSchema.Description("The gender: MALE or FEMALE.")
    @JsonSchema.Example("\"MALE\"")
    val gender: Gender,
    @JsonSchema.Description(
        "The email address the guest logs in with, or null to clear it. Trimmed and stored in lowercase, and a " +
            "blank email clears it. One guest per email.",
    )
    @JsonSchema.Example("\"ola.nordmann@example.com\"")
    val email: String?,
    @JsonSchema.Description("True if the guest belongs to the family.")
    @JsonSchema.Example("true")
    val isFamily: Boolean,
    @JsonSchema.Description(
        "The role in the application: ADMIN, FAMILY_USER or GUEST_USER, or null for no access.",
    )
    @JsonSchema.Example("\"ADMIN\"")
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
    @JsonSchema.Description("The first name, 1-20 characters.")
    @JsonSchema.Example("\"Ola\"")
    val firstName: String,
    @JsonSchema.Description("The last name, 1-20 characters.")
    @JsonSchema.Example("\"Nordmann\"")
    val lastName: String,
    @JsonSchema.Description("The year of birth.")
    @JsonSchema.Example("1985")
    val birthYear: Short,
    @JsonSchema.Description("The gender: MALE or FEMALE.")
    @JsonSchema.Example("\"MALE\"")
    val gender: Gender,
    @JsonSchema.Description(
        "The email address the guest logs in with. Optional: a guest without an email cannot log in. Trimmed and " +
            "stored in lowercase. One guest per email.",
    )
    @JsonSchema.Example("\"ola.nordmann@example.com\"")
    val email: String? = null,
    @JsonSchema.Description("True if the guest belongs to the family. Optional, default false.")
    @JsonSchema.Example("true")
    val isFamily: Boolean = false,
    @JsonSchema.Description(
        "The role in the application: ADMIN, FAMILY_USER or GUEST_USER. Optional: no role means no access.",
    )
    @JsonSchema.Example("\"ADMIN\"")
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
