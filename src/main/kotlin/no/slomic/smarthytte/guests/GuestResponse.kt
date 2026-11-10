package no.slomic.smarthytte.guests

import io.ktor.openapi.JsonSchema
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * A guest as the API returns it. [avatarUrl] is null when the guest has no avatar, and otherwise changes when the
 * image does. [version] is what an update must send back. `createdBy` and `updatedBy` hold a guest id or the literal
 * `system`, which is not a guest.
 */
@Serializable
data class GuestResponse(
    @JsonSchema.Description("Internal unique identifier of the guest, generated from the name, such as ola.nordmann.")
    @JsonSchema.Example("\"ola.nordmann\"")
    val id: String,
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
    @JsonSchema.Description("The email address the guest logs in with, or null if the guest has none.")
    @JsonSchema.Example("\"ola.nordmann@example.com\"")
    val email: String?,
    @JsonSchema.Description("True if the guest belongs to the family.")
    @JsonSchema.Example("true")
    val isFamily: Boolean,
    @JsonSchema.Description("The role in the application: ADMIN, FAMILY_USER or GUEST_USER, or null for no access.")
    @JsonSchema.Example("\"ADMIN\"")
    val role: AppRole?,
    @JsonSchema.Description(
        "Relative URL of the avatar image, or null if the guest has no avatar. The v parameter changes when the " +
            "image does, so the URL can be cached safely.",
    )
    @JsonSchema.Example("\"/api/guests/ola.nordmann/avatar?v=1767225600000\"")
    val avatarUrl: String?,
    @JsonSchema.Description(
        "The version of the guest. Send it back unchanged in PUT /api/guests/{guestId}, an outdated version gives " +
            "412. It is not the version of the avatar.",
    )
    @JsonSchema.Example("3")
    val version: Short,
    @JsonSchema.Description("When the guest was created (UTC).")
    @JsonSchema.Example("\"2026-06-01T09:15:00Z\"")
    val createdTime: Instant,
    @JsonSchema.Description("Who created the guest: a guest id, or the literal system, which is not a guest.")
    @JsonSchema.Example("\"ola.nordmann\"")
    val createdBy: String,
    @JsonSchema.Description("When the guest was last changed (UTC), or null if never changed.")
    @JsonSchema.Example("\"2026-06-20T18:30:00Z\"")
    val updatedTime: Instant?,
    @JsonSchema.Description(
        "Who last changed the guest: a guest id, or the literal system, which is not a guest. Null if never changed.",
    )
    @JsonSchema.Example("\"kari.nordmann\"")
    val updatedBy: String?,
)

fun Guest.toResponse(avatarVersionTime: Instant?) = GuestResponse(
    id = id,
    firstName = firstName,
    lastName = lastName,
    birthYear = birthYear,
    gender = gender,
    email = email,
    isFamily = isFamily,
    role = role,
    avatarUrl = avatarVersionTime?.let { guestAvatarUrl(id, it) },
    version = version,
    createdTime = createdTime,
    createdBy = createdBy,
    updatedTime = updatedTime,
    updatedBy = updatedBy,
)
