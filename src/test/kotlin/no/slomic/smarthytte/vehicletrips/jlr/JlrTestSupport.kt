package no.slomic.smarthytte.vehicletrips.jlr

import io.ktor.http.HttpStatusCode
import java.security.MessageDigest
import java.util.Base64
import kotlin.time.Clock
import kotlin.time.Instant

internal const val TOKEN_URL = "https://auth.jlr-vcdp.com/realms/public/protocol/openid-connect/token"

internal class MutableClock(var current: Instant) : Clock {
    override fun now(): Instant = current
}

internal class Reply(
    val status: HttpStatusCode,
    val body: String,
    val headers: List<Pair<String, String>> = emptyList(),
    val failure: Throwable? = null,
    val bodyFailure: Throwable? = null,
)

internal fun ok(body: String) = Reply(HttpStatusCode.OK, body)

internal fun errorReply(error: String, status: HttpStatusCode = HttpStatusCode.BadRequest, nonce: String? = null) =
    Reply(
        status,
        """{"error":"$error"}""",
        listOfNotNull(nonce?.let { "DPoP-Nonce" to it }),
    )

internal fun tokens(access: String, refresh: String? = null, expiresIn: Long = 300) = ok(
    buildString {
        append("""{"access_token":"$access","token_type":"DPoP","expires_in":$expiresIn,"refresh_expires_in":0""")
        refresh?.let { append(""","refresh_token":"$it"""") }
        append("}")
    },
)

internal fun sha256Base64Url(value: String) = Base64.getUrlEncoder().withoutPadding()
    .encodeToString(MessageDigest.getInstance("SHA-256").digest(value.toByteArray()))
