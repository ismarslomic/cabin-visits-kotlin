package no.slomic.smarthytte.guests

import kotlin.time.Instant

/** Not a data class because of the [ByteArray] property, which has no content-based equals. */
class GuestAvatar(val image: ByteArray, val updatedTime: Instant)

const val GUEST_AVATAR_PATH = "/api/guests/{guestId}/avatar"

/** Relative URL to the avatar. The `v` parameter changes when the image does, so clients can cache it safely. */
fun guestAvatarUrl(guestId: String, updatedTime: Instant): String =
    GUEST_AVATAR_PATH.replace("{guestId}", guestId) + "?v=${updatedTime.toEpochMilliseconds()}"
