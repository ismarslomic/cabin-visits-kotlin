package no.slomic.smarthytte.vehicletrips.jlr

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.math.BigInteger
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.interfaces.RSAPrivateCrtKey
import java.security.spec.MGF1ParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.PSSParameterSpec
import java.util.Base64
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

private val ASSERTION_LIFETIME = 300.seconds
private const val PSS_SALT_LENGTH_BYTES = 32
private const val PSS_TRAILER_FIELD = 1
private const val RSA_KEY_ALGORITHM = "RSA"
private const val MIN_KEY_BITS = 2048

// DER of AlgorithmIdentifier { rsaEncryption, NULL }, the fixed middle part of a PKCS#8 RSA key
private val RSA_ALGORITHM_IDENTIFIER = byteArrayOf(
    0x30, 0x0d, 0x06, 0x09, 0x2a, 0x86.toByte(), 0x48, 0x86.toByte(), 0xf7.toByte(), 0x0d, 0x01, 0x01, 0x01, 0x05, 0x00,
)
private const val DER_SEQUENCE = 0x30
private const val DER_INTEGER = 0x02
private const val DER_OCTET_STRING = 0x04
private const val DER_LONG_FORM_FLAG = 0x80
private const val BYTE_MASK = 0xff
private const val BITS_PER_BYTE = 8

/**
 * Signs the two kinds of JWT the JLR Data Hub wants, both PS256 (RSASSA-PSS, SHA-256, salt 32 bytes) with the private
 * key of the Data Access Credential: the client assertion for the auth server (RFC 7523) and the DPoP proof (RFC 9449).
 *
 * Measured against the API, which differs from its docs in places:
 * - The client assertion is needed on the device endpoint too, not only on the token endpoint.
 * - A DPoP proof for an API call carries `ath` (hash of the access token), a proof for the token endpoint does not.
 * - A proof is single use, so build a new one for every request, also a retry.
 *
 * @param privateKeyPem unencrypted RSA key of at least 2048 bits, PKCS#8 (`BEGIN PRIVATE KEY`) or PKCS#1
 * (`BEGIN RSA PRIVATE KEY`). A literal `\n` instead of line breaks is accepted, as an environment variable cannot
 * hold line breaks easily.
 */
class JlrJwtSigner(privateKeyPem: String, private val clock: Clock = Clock.System) {
    private val privateKey: RSAPrivateCrtKey = parsePrivateKey(privateKeyPem)
    private val publicJwk: JsonObject = buildJsonObject {
        put("kty", "RSA")
        put("n", base64Url(privateKey.modulus.toUnsignedBytes()))
        put("e", base64Url(privateKey.publicExponent.toUnsignedBytes()))
    }

    /** `private_key_jwt` for [audience], the token URL of the auth server. */
    fun clientAssertion(clientId: String, audience: String): String {
        val issuedAt = clock.now().epochSeconds
        val claims = buildJsonObject {
            put("iss", clientId)
            put("sub", clientId)
            put("aud", audience)
            put("jti", UUID.randomUUID().toString())
            put("iat", issuedAt)
            put("exp", issuedAt + ASSERTION_LIFETIME.inWholeSeconds)
        }
        val header = buildJsonObject {
            put("alg", "PS256")
            put("typ", "JWT")
        }
        return sign(header, claims)
    }

    /**
     * DPoP proof for one request. [url] may have a query, it is left out of `htu`. Pass [accessToken] for an API call,
     * leave it out for a request to the token endpoint. Pass [nonce] only after the server answered `use_dpop_nonce`
     * with a `DPoP-Nonce` header, and build a new proof for that retry.
     */
    fun dpopProof(method: String, url: String, accessToken: String? = null, nonce: String? = null): String {
        val claims = buildJsonObject {
            put("jti", UUID.randomUUID().toString())
            put("htm", method.uppercase())
            put("htu", url.substringBefore('?').substringBefore('#'))
            put("iat", clock.now().epochSeconds)
            accessToken?.let { put("ath", base64Url(sha256(it.toByteArray()))) }
            nonce?.let { put("nonce", it) }
        }
        val header = buildJsonObject {
            put("typ", "dpop+jwt")
            put("alg", "PS256")
            put("jwk", publicJwk)
        }
        return sign(header, claims)
    }

    private fun sign(header: JsonObject, claims: JsonObject): String {
        val signingInput = "${base64Url(header.toString().toByteArray())}.${base64Url(claims.toString().toByteArray())}"
        val signature = Signature.getInstance("RSASSA-PSS").run {
            setParameter(
                PSSParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSS_SALT_LENGTH_BYTES, PSS_TRAILER_FIELD),
            )
            initSign(privateKey)
            update(signingInput.toByteArray())
            sign()
        }
        return "$signingInput.${base64Url(signature)}"
    }
}

private fun base64Url(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

private fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)

/** [BigInteger.toByteArray] adds a leading zero byte when the top bit is set, JWK wants the number without it. */
private fun BigInteger.toUnsignedBytes(): ByteArray {
    val bytes = toByteArray()
    val hasSignByte = bytes.size > 1 && bytes[0] == 0.toByte()
    return if (hasSignByte) bytes.copyOfRange(1, bytes.size) else bytes
}

private fun parsePrivateKey(pem: String): RSAPrivateCrtKey {
    val normalized = pem.trim().replace("\\n", "\n")
    require("ENCRYPTED" !in normalized) { "The JLR private key is encrypted, use an unencrypted key" }
    val isPkcs1 = "BEGIN RSA PRIVATE KEY" in normalized
    val base64 = normalized.replace(Regex("-----[^-]+-----"), "").replace(Regex("\\s+"), "")
    require(base64.isNotEmpty()) { "The JLR private key is empty or not a PEM" }
    val der = runCatching { Base64.getDecoder().decode(base64) }
        .getOrElse { throw IllegalArgumentException("The JLR private key is not valid base64") }
    val pkcs8 = if (isPkcs1) wrapPkcs1InPkcs8(der) else der
    val key = runCatching { KeyFactory.getInstance(RSA_KEY_ALGORITHM).generatePrivate(PKCS8EncodedKeySpec(pkcs8)) }
        .getOrElse { throw IllegalArgumentException("The JLR private key is not a valid RSA private key") }
    val crtKey =
        requireNotNull(key as? RSAPrivateCrtKey) { "The JLR private key is not an RSA key with CRT parameters" }
    require(crtKey.modulus.bitLength() >= MIN_KEY_BITS) { "The JLR private key is shorter than $MIN_KEY_BITS bits" }
    return crtKey
}

/** PKCS#8 is `SEQUENCE { INTEGER 0, AlgorithmIdentifier, OCTET STRING { the PKCS#1 key } }`. */
private fun wrapPkcs1InPkcs8(pkcs1: ByteArray): ByteArray {
    val version = byteArrayOf(DER_INTEGER.toByte(), 1, 0)
    val octetString = derElement(DER_OCTET_STRING, pkcs1)
    return derElement(DER_SEQUENCE, version + RSA_ALGORITHM_IDENTIFIER + octetString)
}

private fun derElement(tag: Int, content: ByteArray): ByteArray =
    byteArrayOf(tag.toByte()) + derLength(content.size) + content

private fun derLength(length: Int): ByteArray = if (length < DER_LONG_FORM_FLAG) {
    byteArrayOf(length.toByte())
} else {
    val lengthBytes = generateSequence(length) { it shr BITS_PER_BYTE }
        .takeWhile { it > 0 }
        .map { (it and BYTE_MASK).toByte() }
        .toList()
        .reversed()
        .toByteArray()
    byteArrayOf((DER_LONG_FORM_FLAG or lengthBytes.size).toByte()) + lengthBytes
}
