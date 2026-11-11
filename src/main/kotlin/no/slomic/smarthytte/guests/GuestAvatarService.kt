package no.slomic.smarthytte.guests

import kotlin.time.Instant

/** What [GuestAvatarService.upload] did. */
sealed interface AvatarUploadResult {
    /** The avatar is stored: new, replaced, or the same image as before. */
    data object Saved : AvatarUploadResult
    data object GuestNotFound : AvatarUploadResult

    /** The bytes are not a JPEG that can be stored, [reason] says why. */
    data class InvalidImage(val reason: String) : AvatarUploadResult
}

/**
 * Stores uploaded avatars (the metadata is stripped, see [sanitizeJpeg], before the image is saved) and reads them,
 * also for other packages. The reads apply no access control: the route that serves them decides who may call it.
 */
class GuestAvatarService(private val guestRepository: GuestRepository) {
    /** Creates or replaces the avatar of [guestId] with [image], on behalf of [actor] (a guest id). */
    suspend fun upload(guestId: String, image: ByteArray, actor: String): AvatarUploadResult =
        if (guestRepository.guestById(guestId) == null) {
            AvatarUploadResult.GuestNotFound
        } else {
            when (val sanitized = sanitizeJpeg(image)) {
                is JpegSanitizeResult.Invalid -> AvatarUploadResult.InvalidImage(sanitized.reason)

                is JpegSanitizeResult.Sanitized -> {
                    guestRepository.saveAvatar(guestId, sanitized.bytes, actor)
                    AvatarUploadResult.Saved
                }
            }
        }

    suspend fun avatarById(guestId: String): GuestAvatar? = guestRepository.avatarById(guestId)

    /** The version time of every avatar by guest id, only for guests with an avatar (see [GuestRepository]). */
    suspend fun allAvatarVersionTimes(): Map<String, Instant> = guestRepository.allAvatarVersionTimes()

    /** The avatar's version time of one guest, or null without an avatar. */
    suspend fun avatarVersionTime(guestId: String): Instant? = guestRepository.avatarVersionTime(guestId)
}
