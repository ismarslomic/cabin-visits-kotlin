package no.slomic.smarthytte.guests

import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * A guest as the API returns it. [avatarUrl] is null when the guest has no avatar, and otherwise changes when the
 * image does. [version] is what an update must send back. `createdBy` and `updatedBy` hold a guest id or the literal
 * `system`, which is not a guest.
 */
@Serializable
data class GuestResponse(
    val id: String,
    val firstName: String,
    val lastName: String,
    val birthYear: Short,
    val gender: Gender,
    val email: String?,
    val isFamily: Boolean,
    val role: AppRole?,
    val avatarUrl: String?,
    val version: Short,
    val createdTime: Instant,
    val createdBy: String,
    val updatedTime: Instant?,
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
