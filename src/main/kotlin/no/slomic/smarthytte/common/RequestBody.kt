package no.slomic.smarthytte.common

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.log
import io.ktor.server.request.contentLength
import io.ktor.server.request.contentType
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.respond
import io.ktor.utils.io.readBuffer
import kotlinx.io.readByteArray
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** The largest JSON request body that is read. The routes only receive a few small fields. */
const val MAX_JSON_BODY_BYTES = 64 * 1024L

/** Same settings as the `ContentNegotiation` plugin, so a body is read the same way as before. */
@PublishedApi
internal val requestJson = Json {
    isLenient = true
    ignoreUnknownKeys = true
}

/**
 * The outcome of reading a JSON request body. Every state-changing route requires `application/json` (415 otherwise),
 * which keeps cross-site form posts out: they cannot send that content type without a CORS preflight.
 */
sealed interface RequestBody<out T> {
    data class Ok<T>(val value: T) : RequestBody<T>
    data object UnsupportedMediaType : RequestBody<Nothing>
    data object TooLarge : RequestBody<Nothing>

    /** Not valid JSON for the type, or a required property is missing. */
    data object Malformed : RequestBody<Nothing>
}

/**
 * Reads the request body as JSON for [T]: `application/json` required, at most [MAX_JSON_BODY_BYTES] are read (the
 * rest of a larger body is not buffered). A malformed body is logged as a warning.
 */
suspend inline fun <reified T : Any> ApplicationCall.receiveJsonBody(): RequestBody<T> {
    val readable = readableJsonBody()
    if (readable != null) return readable

    val bytes = receiveChannel().readBuffer(MAX_JSON_BODY_BYTES + 1).readByteArray()
    return if (bytes.size > MAX_JSON_BODY_BYTES) {
        RequestBody.TooLarge
    } else {
        try {
            RequestBody.Ok(requestJson.decodeFromString<T>(bytes.decodeToString()))
        } catch (e: SerializationException) {
            logUnreadableBody(e)
            RequestBody.Malformed
        } catch (e: IllegalArgumentException) {
            logUnreadableBody(e)
            RequestBody.Malformed
        }
    }
}

/**
 * Reads the request body as raw bytes: the `Content-Type` must be [contentType] (parameters such as `charset` are
 * ignored, [RequestBody.UnsupportedMediaType] otherwise) and at most [maxBytes] are read, the rest of a larger body is
 * not buffered ([RequestBody.TooLarge]).
 */
suspend fun ApplicationCall.receiveBinaryBody(contentType: ContentType, maxBytes: Long): RequestBody<ByteArray> {
    val isExpectedType = runCatching { request.contentType().match(contentType) }.getOrDefault(false)
    val length = request.contentLength()

    return when {
        !isExpectedType -> RequestBody.UnsupportedMediaType

        length != null && length > maxBytes -> RequestBody.TooLarge

        else -> {
            val bytes = receiveChannel().readBuffer(maxBytes + 1).readByteArray()
            if (bytes.size > maxBytes) RequestBody.TooLarge else RequestBody.Ok(bytes)
        }
    }
}

/** A failure result if the body must not be read ([RequestBody.UnsupportedMediaType] or too large), otherwise null. */
@PublishedApi
internal fun ApplicationCall.readableJsonBody(): RequestBody<Nothing>? {
    val isJson = runCatching { request.contentType().match(ContentType.Application.Json) }.getOrDefault(false)
    val length = request.contentLength()

    return when {
        !isJson -> RequestBody.UnsupportedMediaType
        length != null && length > MAX_JSON_BODY_BYTES -> RequestBody.TooLarge
        else -> null
    }
}

@PublishedApi
internal fun ApplicationCall.logUnreadableBody(cause: Exception) {
    application.log.warn("Could not read the request body of ${request.httpMethod.value} ${request.path()}", cause)
}

/** Responds the plain-text error of the existing guest routes for a [RequestBody] that is not [RequestBody.Ok]. */
suspend fun ApplicationCall.respondPlainBodyError(body: RequestBody<*>) {
    when (body) {
        is RequestBody.Ok -> error("The body was read")

        RequestBody.UnsupportedMediaType ->
            respond(HttpStatusCode.UnsupportedMediaType, "Content-Type must be application/json")

        RequestBody.TooLarge -> respond(HttpStatusCode.PayloadTooLarge, "The request body is too large")

        RequestBody.Malformed -> respond(HttpStatusCode.BadRequest, "Invalid request body")
    }
}

/** Responds the [ApiError] of the new routes for a [RequestBody] that is not [RequestBody.Ok]. */
suspend fun ApplicationCall.respondApiBodyError(body: RequestBody<*>) {
    when (body) {
        is RequestBody.Ok -> error("The body was read")

        RequestBody.UnsupportedMediaType -> respond(
            HttpStatusCode.UnsupportedMediaType,
            ApiError(ApiError.UNSUPPORTED_MEDIA_TYPE, "Content-Type must be application/json"),
        )

        RequestBody.TooLarge -> respond(
            HttpStatusCode.PayloadTooLarge,
            ApiError(ApiError.BODY_TOO_LARGE, "The request body is too large"),
        )

        RequestBody.Malformed -> respond(
            HttpStatusCode.BadRequest,
            ApiError(ApiError.INVALID_REQUEST, "The body is not valid JSON or a required property is missing"),
        )
    }
}
