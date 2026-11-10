package no.slomic.smarthytte.auth

import io.ktor.openapi.JsonSchema
import kotlinx.serialization.Serializable
import no.slomic.smarthytte.guests.AppRole
import no.slomic.smarthytte.guests.Gender
import no.slomic.smarthytte.guests.guestAvatarUrl
import kotlin.time.Instant

/**
 * The authenticated user as `/api/me` returns it: the [UserPrincipal] together with [avatarUrl], which is null without
 * an avatar and otherwise changes when the image does. [version] is what `PUT /api/me` must send back.
 */
@Serializable
data class MeResponse(
    @JsonSchema.Description("Internal unique identifier of the guest, generated from the name, such as ola.nordmann.")
    @JsonSchema.Example("\"ola.nordmann\"")
    val id: String,
    @JsonSchema.Description("The first and last name together.")
    @JsonSchema.Example("\"Ola Nordmann\"")
    val name: String,
    @JsonSchema.Description("The first name.")
    @JsonSchema.Example("\"Ola\"")
    val firstName: String,
    @JsonSchema.Description("The last name.")
    @JsonSchema.Example("\"Nordmann\"")
    val lastName: String,
    @JsonSchema.Description("The year of birth.")
    @JsonSchema.Example("1985")
    val birthYear: Short,
    @JsonSchema.Description("The gender: MALE or FEMALE.")
    @JsonSchema.Example("\"MALE\"")
    val gender: Gender,
    @JsonSchema.Description("The email address the user logs in with. Only an admin can change it.")
    @JsonSchema.Example("\"ola.nordmann@example.com\"")
    val email: String,
    @JsonSchema.Description("True if the guest belongs to the family. Only an admin can change it.")
    @JsonSchema.Example("true")
    val isFamily: Boolean,
    @JsonSchema.Description(
        "The role in the application: ADMIN, FAMILY_USER or GUEST_USER. Only an admin can change it.",
    )
    @JsonSchema.Example("\"ADMIN\"")
    val role: AppRole,
    @JsonSchema.Description(
        "Relative URL of the avatar image, or null if the guest has no avatar. The v parameter changes when the " +
            "image does, so the URL can be cached safely. Fetch /api/me again after uploading an avatar.",
    )
    @JsonSchema.Example("\"/api/guests/ola.nordmann/avatar?v=1767225600000\"")
    val avatarUrl: String?,
    @JsonSchema.Description(
        "The version of the guest. Send it back unchanged in PUT /api/me, an outdated version gives 412. It is " +
            "not the version of the avatar.",
    )
    @JsonSchema.Example("3")
    val version: Short,
)

fun UserPrincipal.toMeResponse(avatarVersionTime: Instant?) = MeResponse(
    id = id,
    name = name,
    firstName = firstName,
    lastName = lastName,
    birthYear = birthYear,
    gender = gender,
    email = email,
    isFamily = isFamily,
    role = role,
    avatarUrl = avatarVersionTime?.let { guestAvatarUrl(id, it) },
    version = version,
)
