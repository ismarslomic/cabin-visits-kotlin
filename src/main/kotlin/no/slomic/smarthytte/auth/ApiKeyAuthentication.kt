package no.slomic.smarthytte.auth

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.AuthenticationConfig
import io.ktor.server.auth.apikey.ApiKeyAuth
import io.ktor.server.auth.apikey.apiKey
import io.ktor.server.response.respond
import no.slomic.smarthytte.properties.ApiKeyProperties
import java.security.MessageDigest
import java.util.HexFormat

/** Name of the authentication provider for services, see [authorized]. */
const val API_KEY_AUTH = "api-key"

/** The header that carries the API key. */
const val API_KEY_HEADER = ApiKeyAuth.DEFAULT_HEADER_NAME

private const val SHA256_BYTES = 32

/** The services that may authenticate with an API key, identified by the SHA-256 of their key. */
class ApiKeyConfig(private val sha256ByName: Map<String, ByteArray>) {
    /** The service whose key is [apiKey], or null. Compares in constant time and does not stop at the first match. */
    fun principalFor(apiKey: String): ServicePrincipal? {
        val hash = sha256(apiKey)
        return sha256ByName.entries
            .map { (name, expected) -> name.takeIf { MessageDigest.isEqual(hash, expected) } }
            .firstNotNullOfOrNull { it }
            ?.let(::ServicePrincipal)
    }
}

/** Throws at startup when an entry is malformed, so a typo cannot silently leave a service without access. */
fun ApiKeyProperties.toConfig(): ApiKeyConfig {
    val sha256ByName = keys.split(',')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .associate { entry ->
            val (name, hex) = entry.split(':', limit = 2).let { it.first().trim() to it.getOrNull(1)?.trim() }
            require(name.isNotEmpty() && hex != null) { "API key entry must be name:sha256, but was '$entry'" }
            val hash = runCatching { HexFormat.of().parseHex(hex) }.getOrNull()
            require(hash?.size == SHA256_BYTES) { "API key '$name' must be a hex encoded SHA-256 (64 characters)" }
            name to hash
        }

    return ApiKeyConfig(sha256ByName)
}

/** Hex encoded SHA-256, the format of the configured API keys. */
fun sha256Hex(value: String): String = HexFormat.of().formatHex(sha256(value))

private fun sha256(value: String): ByteArray = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())

internal fun AuthenticationConfig.apiKeyAuthentication(config: ApiKeyConfig) {
    apiKey(API_KEY_AUTH) {
        headerName = API_KEY_HEADER
        validate { key -> config.principalFor(key) }
        challenge { call -> call.respond(HttpStatusCode.Unauthorized, "Invalid or missing authentication") }
    }
}
