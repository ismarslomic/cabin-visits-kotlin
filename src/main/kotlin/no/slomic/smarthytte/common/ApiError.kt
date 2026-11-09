package no.slomic.smarthytte.common

import io.ktor.openapi.JsonSchema
import kotlinx.serialization.Serializable

/**
 * The error body of the new API routes: the HTTP status code says what kind of error it is, [code] is a stable
 * machine-readable reason that clients map to their own texts, and [message] is an English description for developers.
 */
@Serializable
data class ApiError(
    @JsonSchema.Description(
        "Stable machine-readable reason, for example INVALID_REQUEST, INVALID_GUEST, INVALID_IMAGE, NOT_FOUND, " +
            "FORBIDDEN, EMAIL_IN_USE, LAST_ADMIN, VERSION_MISMATCH, BODY_TOO_LARGE, UNSUPPORTED_MEDIA_TYPE or " +
            "TEMPORARILY_UNAVAILABLE. Clients map it to their own texts.",
    )
    val code: String,
    @JsonSchema.Description("English description of the error for developers. Not meant to be shown to users.")
    val message: String,
) {
    companion object {
        const val INVALID_REQUEST = "INVALID_REQUEST"
        const val INVALID_GUEST = "INVALID_GUEST"
        const val INVALID_IMAGE = "INVALID_IMAGE"
        const val NOT_FOUND = "NOT_FOUND"
        const val EMAIL_IN_USE = "EMAIL_IN_USE"
        const val LAST_ADMIN = "LAST_ADMIN"
        const val FORBIDDEN = "FORBIDDEN"
        const val VERSION_MISMATCH = "VERSION_MISMATCH"
        const val BODY_TOO_LARGE = "BODY_TOO_LARGE"
        const val UNSUPPORTED_MEDIA_TYPE = "UNSUPPORTED_MEDIA_TYPE"
        const val TEMPORARILY_UNAVAILABLE = "TEMPORARILY_UNAVAILABLE"
    }
}
