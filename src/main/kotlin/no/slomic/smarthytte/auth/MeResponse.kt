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
    val id: String,
    @JsonSchema.Description("The first and last name together.")
    val name: String,
    @JsonSchema.Description("The first name.")
    val firstName: String,
    @JsonSchema.Description("The last name.")
    val lastName: String,
    @JsonSchema.Description("The year of birth.")
    val birthYear: Short,
    @JsonSchema.Description("The gender: MALE or FEMALE.")
    val gender: Gender,
    @JsonSchema.Description("The email address the user logs in with. Only an admin can change it.")
    val email: String,
    @JsonSchema.Description("True if the guest belongs to the family. Only an admin can change it.")
    val isFamily: Boolean,
    @JsonSchema.Description(
        "The role in the application: ADMIN, FAMILY_USER or GUEST_USER. Only an admin can change it.",
    )
    val role: AppRole,
    @JsonSchema.Description(
        "Relative URL of the avatar image, or null if the guest has no avatar. The v parameter changes when the " +
            "image does, so the URL can be cached safely. Fetch /api/me again after uploading an avatar.",
    )
    val avatarUrl: String?,
    @JsonSchema.Description(
        "The version of the guest. Send it back unchanged in PUT /api/me, an outdated version gives 412. It is " +
            "not the version of the avatar.",
    )
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
