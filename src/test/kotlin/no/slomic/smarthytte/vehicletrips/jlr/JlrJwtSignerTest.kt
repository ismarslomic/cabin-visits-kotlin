package no.slomic.smarthytte.vehicletrips.jlr

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.security.interfaces.RSAPublicKey
import java.security.spec.MGF1ParameterSpec
import java.security.spec.PSSParameterSpec
import java.security.spec.RSAPublicKeySpec
import java.util.Base64
import kotlin.time.Clock
import kotlin.time.Instant

private val FIXED_NOW = Instant.parse("2026-11-15T10:00:00Z")
private val fixedClock = object : Clock {
    override fun now(): Instant = FIXED_NOW
}

private const val PKCS8_RSA_HEADER_SIZE = 26
private const val PEM_LINE_LENGTH = 64

private val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
private val pkcs8Der: ByteArray = keyPair.private.encoded

// A PKCS#8 RSA key of 2048 bits or more is a fixed 26 byte header followed by the PKCS#1 key
private val pkcs1Der: ByteArray = pkcs8Der.copyOfRange(PKCS8_RSA_HEADER_SIZE, pkcs8Der.size)

private fun pem(label: String, der: ByteArray, lineBreak: String = "\n"): String {
    val body = Base64.getEncoder().encodeToString(der).chunked(PEM_LINE_LENGTH).joinToString(lineBreak)
    return "-----BEGIN $label-----$lineBreak$body$lineBreak-----END $label-----"
}

private fun base64UrlDecode(value: String): ByteArray = Base64.getUrlDecoder().decode(value)

private class ParsedJwt(
    val header: JsonObject,
    val claims: JsonObject,
    val signingInput: String,
    val signature: ByteArray,
)

private fun parse(jwt: String): ParsedJwt {
    val (header, claims, signature) = jwt.split('.')
    return ParsedJwt(
        header = Json.parseToJsonElement(String(base64UrlDecode(header))).jsonObject,
        claims = Json.parseToJsonElement(String(base64UrlDecode(claims))).jsonObject,
        signingInput = "$header.$claims",
        signature = base64UrlDecode(signature),
    )
}

private fun ParsedJwt.isSignedWith(publicKey: java.security.PublicKey): Boolean =
    Signature.getInstance("RSASSA-PSS").run {
        setParameter(PSSParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, 32, 1))
        initVerify(publicKey)
        update(signingInput.toByteArray())
        verify(signature)
    }

class JlrJwtSignerTest :
    ShouldSpec({
        val tokenUrl = "https://auth.example.test/realms/public/protocol/openid-connect/token"
        val signer = JlrJwtSigner(pem("PRIVATE KEY", pkcs8Der), fixedClock)

        context("client assertion") {
            val assertion = parse(signer.clientAssertion(clientId = "client-1", audience = tokenUrl))

            should("have a PS256 JWT header without kid") {
                assertion.header.keys shouldBe setOf("alg", "typ")
                assertion.header["alg"]?.jsonPrimitive?.content shouldBe "PS256"
                assertion.header["typ"]?.jsonPrimitive?.content shouldBe "JWT"
            }

            should("carry the client as issuer and subject, the token URL as audience and a 5 minute lifetime") {
                assertion.claims["iss"]?.jsonPrimitive?.content shouldBe "client-1"
                assertion.claims["sub"]?.jsonPrimitive?.content shouldBe "client-1"
                assertion.claims["aud"]?.jsonPrimitive?.content shouldBe tokenUrl
                assertion.claims["iat"]?.jsonPrimitive?.long shouldBe FIXED_NOW.epochSeconds
                assertion.claims["exp"]?.jsonPrimitive?.long shouldBe FIXED_NOW.epochSeconds + 300
            }

            should("be signed with RSASSA-PSS so the public key verifies it") {
                assertion.isSignedWith(keyPair.public) shouldBe true
            }

            should("get a new jti every time") {
                val other = parse(signer.clientAssertion(clientId = "client-1", audience = tokenUrl))

                (other.claims["jti"] == assertion.claims["jti"]) shouldBe false
            }
        }

        context("DPoP proof") {
            should("have the dpop+jwt header with the public key as JWK") {
                val proof = parse(signer.dpopProof("POST", tokenUrl))

                proof.header["typ"]?.jsonPrimitive?.content shouldBe "dpop+jwt"
                proof.header["alg"]?.jsonPrimitive?.content shouldBe "PS256"
                val jwk = proof.header["jwk"]?.jsonObject.let(::requireNotNull)
                jwk.keys shouldBe setOf("kty", "n", "e")
                jwk["kty"]?.jsonPrimitive?.content shouldBe "RSA"
                val publicKey = keyPair.public as RSAPublicKey
                jwk["n"]?.jsonPrimitive?.content?.let { BigInteger(1, base64UrlDecode(it)) } shouldBe publicKey.modulus
                jwk["e"]?.jsonPrimitive?.content?.let { BigInteger(1, base64UrlDecode(it)) } shouldBe
                    publicKey.publicExponent
            }

            should("be verifiable with the key from its own JWK") {
                val proof = parse(signer.dpopProof("GET", "https://api.example.test/v1/vehicles/V1/journeys"))
                val jwk = proof.header["jwk"]?.jsonObject.let(::requireNotNull)
                val jwkKey = java.security.KeyFactory.getInstance("RSA").generatePublic(
                    RSAPublicKeySpec(
                        BigInteger(1, base64UrlDecode(jwk["n"]?.jsonPrimitive?.content.let(::requireNotNull))),
                        BigInteger(1, base64UrlDecode(jwk["e"]?.jsonPrimitive?.content.let(::requireNotNull))),
                    ),
                )

                proof.isSignedWith(jwkKey) shouldBe true
            }

            should("leave the query and fragment out of htu and upper case the method") {
                val proof = parse(signer.dpopProof("get", "https://api.example.test/v1/x?page=0&size=100#top"))

                proof.claims["htm"]?.jsonPrimitive?.content shouldBe "GET"
                proof.claims["htu"]?.jsonPrimitive?.content shouldBe "https://api.example.test/v1/x"
                proof.claims["iat"]?.jsonPrimitive?.long shouldBe FIXED_NOW.epochSeconds
            }

            should("have no ath for the token endpoint") {
                parse(signer.dpopProof("POST", tokenUrl)).claims.containsKey("ath") shouldBe false
            }

            should("have ath equal to the base64url SHA-256 of the access token for an API call") {
                val proof = parse(signer.dpopProof("GET", "https://api.example.test/v1/x", accessToken = "token-1"))

                val expected = MessageDigest.getInstance("SHA-256").digest("token-1".toByteArray())
                proof.claims["ath"]?.jsonPrimitive?.content shouldBe
                    Base64.getUrlEncoder().withoutPadding().encodeToString(expected)
            }

            should("have no nonce unless the server asked for one") {
                parse(signer.dpopProof("POST", tokenUrl)).claims.containsKey("nonce") shouldBe false
            }

            should("carry the nonce from the server when given") {
                val proof = parse(signer.dpopProof("POST", tokenUrl, nonce = "server-nonce"))

                proof.claims["nonce"]?.jsonPrimitive?.content shouldBe "server-nonce"
                proof.isSignedWith(keyPair.public) shouldBe true
            }

            should("get a new jti every time, as a proof is single use") {
                val first = parse(signer.dpopProof("POST", tokenUrl)).claims["jti"]
                val second = parse(signer.dpopProof("POST", tokenUrl)).claims["jti"]

                (first == second) shouldBe false
            }
        }

        context("private key formats") {
            val tokenAssertion = { s: JlrJwtSigner -> parse(s.clientAssertion("c", tokenUrl)) }

            should("accept PKCS#8") {
                tokenAssertion(JlrJwtSigner(pem("PRIVATE KEY", pkcs8Der), fixedClock))
                    .isSignedWith(keyPair.public) shouldBe true
            }

            should("accept PKCS#1") {
                tokenAssertion(JlrJwtSigner(pem("RSA PRIVATE KEY", pkcs1Der), fixedClock))
                    .isSignedWith(keyPair.public) shouldBe true
            }

            should("accept a literal backslash n instead of line breaks, as in an environment variable") {
                tokenAssertion(JlrJwtSigner(pem("PRIVATE KEY", pkcs8Der, lineBreak = "\\n"), fixedClock))
                    .isSignedWith(keyPair.public) shouldBe true
            }

            should("accept surrounding whitespace and CRLF line breaks") {
                tokenAssertion(
                    JlrJwtSigner("\n  " + pem("PRIVATE KEY", pkcs8Der, lineBreak = "\r\n") + "\n", fixedClock),
                )
                    .isSignedWith(keyPair.public) shouldBe true
            }

            should("reject an encrypted key") {
                shouldThrow<IllegalArgumentException> {
                    JlrJwtSigner(pem("ENCRYPTED PRIVATE KEY", pkcs8Der))
                }.message shouldBe "The JLR private key is encrypted, use an unencrypted key"
            }

            should("reject a legacy PKCS#1 encrypted key") {
                val legacy = listOf(
                    "-----BEGIN RSA PRIVATE KEY-----",
                    "Proc-Type: 4,ENCRYPTED",
                    "DEK-Info: AES-128-CBC,00",
                    "",
                    "AAAA",
                    "-----END RSA PRIVATE KEY-----",
                ).joinToString("\n")

                shouldThrow<IllegalArgumentException> { JlrJwtSigner(legacy) }.message shouldBe
                    "The JLR private key is encrypted, use an unencrypted key"
            }

            should("reject an empty key") {
                shouldThrow<IllegalArgumentException> { JlrJwtSigner("  ") }.message shouldBe
                    "The JLR private key is empty or not a PEM"
            }

            should("accept a PKCS#1 key of 3072 bits, which has a longer DER length than 2048 bits") {
                val bigKeyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(3072) }.generateKeyPair()
                val pkcs8 = bigKeyPair.private.encoded
                val pkcs1 = pkcs8.copyOfRange(PKCS8_RSA_HEADER_SIZE, pkcs8.size)

                tokenAssertion(JlrJwtSigner(pem("RSA PRIVATE KEY", pkcs1), fixedClock))
                    .isSignedWith(bigKeyPair.public) shouldBe true
            }

            should("reject a key shorter than 2048 bits") {
                val weak = KeyPairGenerator.getInstance("RSA").apply { initialize(1024) }.generateKeyPair()

                shouldThrow<IllegalArgumentException> {
                    JlrJwtSigner(pem("PRIVATE KEY", weak.private.encoded))
                }.message shouldBe "The JLR private key is shorter than 2048 bits"
            }

            should("reject a PEM that is not a key, without the key text in the message") {
                val garbage = pem("PRIVATE KEY", "not a key at all".toByteArray())

                val error = shouldThrow<IllegalArgumentException> { JlrJwtSigner(garbage) }

                error.message.orEmpty() shouldNotContain
                    Base64.getEncoder().encodeToString("not a key at all".toByteArray())
            }

            should("reject text that is not base64") {
                shouldThrow<IllegalArgumentException> {
                    JlrJwtSigner("-----BEGIN PRIVATE KEY-----\n***\n-----END PRIVATE KEY-----")
                }.message shouldBe "The JLR private key is not valid base64"
            }
        }
    })
