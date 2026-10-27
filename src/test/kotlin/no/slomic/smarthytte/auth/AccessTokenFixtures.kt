package no.slomic.smarthytte.auth

import com.auth0.jwk.Jwk
import com.auth0.jwk.JwkProvider
import com.auth0.jwk.SigningKeyNotFoundException
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.util.Base64
import java.util.Date
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

const val TEST_ISSUER = "https://myteam.cloudflareaccess.com"
const val TEST_AUDIENCE = "test-audience-tag"
const val TEST_KID = "test-kid"

private fun generateKeyPair(): KeyPair = KeyPairGenerator.getInstance("RSA").apply {
    initialize(2048)
}.generateKeyPair()

/** The key Cloudflare signs with in the tests. */
val cloudflareKeyPair: KeyPair = generateKeyPair()

/** A key that is not published by Cloudflare. */
val otherKeyPair: KeyPair = generateKeyPair()

private val base64Url = Base64.getUrlEncoder().withoutPadding()

private fun BigInteger.toBase64Url(): String =
    base64Url.encodeToString(toByteArray().dropWhile { it == 0.toByte() }.toByteArray())

/** Publishes only [TEST_KID], like the Cloudflare certs endpoint. */
val testJwkProvider = JwkProvider { kid ->
    if (kid != TEST_KID) throw SigningKeyNotFoundException("No key with id $kid", null)

    val publicKey = cloudflareKeyPair.public as RSAPublicKey
    Jwk(
        kid,
        "RSA",
        "RS256",
        "sig",
        emptyList(),
        null,
        null,
        null,
        mapOf("n" to publicKey.modulus.toBase64Url(), "e" to publicKey.publicExponent.toBase64Url()),
    )
}

val testCloudflareAccess = CloudflareAccessConfig(TEST_ISSUER, TEST_AUDIENCE, testJwkProvider)

/** A token Cloudflare Access would issue. Override a parameter to make it invalid in one way. */
@Suppress("LongParameterList")
fun accessToken(
    email: String? = "user@example.com",
    issuer: String = TEST_ISSUER,
    audience: List<String> = listOf(TEST_AUDIENCE),
    kid: String? = TEST_KID,
    expiresIn: Duration = 1.hours,
    notBeforeIn: Duration = Duration.ZERO,
    algorithm: Algorithm = Algorithm.RSA256(
        cloudflareKeyPair.public as RSAPublicKey,
        cloudflareKeyPair.private as RSAPrivateKey,
    ),
): String {
    val now = Clock.System.now()

    return JWT.create()
        .withKeyId(kid)
        .withIssuer(issuer)
        .withAudience(*audience.toTypedArray())
        .withSubject("subject-1")
        .withClaim("email", email)
        .withIssuedAt(Date(now.toEpochMilliseconds()))
        .withNotBefore(Date((now + notBeforeIn).toEpochMilliseconds()))
        .withExpiresAt(Date((now + expiresIn).toEpochMilliseconds()))
        .sign(algorithm)
}

/** Signed with a key that Cloudflare does not publish. */
fun tokenSignedByOtherKey(): String = accessToken(
    algorithm = Algorithm.RSA256(otherKeyPair.public as RSAPublicKey, otherKeyPair.private as RSAPrivateKey),
)
