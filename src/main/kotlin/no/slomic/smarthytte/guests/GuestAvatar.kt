package no.slomic.smarthytte.guests

import kotlin.time.Instant

/**
 * Not a data class because of the [ByteArray] property, which has no content-based equals.
 *
 * [versionTime] is when the image was last changed: `updated_time`, or `created_time` for an avatar never replaced.
 */
class GuestAvatar(val image: ByteArray, val versionTime: Instant)

/**
 * The largest avatar upload, in bytes. The largest stored avatar is 131 KB, so this leaves room for a phone photo
 * that the web app does not resize.
 */
const val MAX_AVATAR_BYTES = 4 * 1024 * 1024L

const val GUEST_AVATAR_PATH = "/api/guests/{guestId}/avatar"

/** Relative URL to the avatar. The `v` parameter changes when the image does, so clients can cache it safely. */
fun guestAvatarUrl(guestId: String, versionTime: Instant): String =
    GUEST_AVATAR_PATH.replace("{guestId}", guestId) + "?v=${versionTime.toEpochMilliseconds()}"
