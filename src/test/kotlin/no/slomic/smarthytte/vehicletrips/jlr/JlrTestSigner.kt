package no.slomic.smarthytte.vehicletrips.jlr

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.util.Base64
import kotlin.time.Clock

private const val PEM_LINE_LENGTH = 64
private const val TEST_KEY_BITS = 2048

/** A throwaway key for tests, never a real credential. */
val testKeyPair: KeyPair by lazy {
    KeyPairGenerator.getInstance("RSA").apply { initialize(TEST_KEY_BITS) }.generateKeyPair()
}

fun testSigner(clock: Clock = Clock.System): JlrJwtSigner {
    val body = Base64.getEncoder().encodeToString(testKeyPair.private.encoded).chunked(PEM_LINE_LENGTH)
    val pem = buildList {
        addAll(listOf("-----BEGIN PRIVATE KEY-----"))
        addAll(body)
        add("-----END PRIVATE KEY-----")
    }.joinToString("\n")
    return JlrJwtSigner(pem, clock)
}

/** The claims of a JWT, without checking the signature (the signer's own test does that). */
fun jwtClaims(jwt: String): JsonObject =
    Json.parseToJsonElement(String(Base64.getUrlDecoder().decode(jwt.split('.')[1]))).jsonObject
