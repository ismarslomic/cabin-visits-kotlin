package no.slomic.smarthytte.vehicletrips.jlr

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.forms.FormDataContent
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.headersOf
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.jsonPrimitive
import no.slomic.smarthytte.issues.IssueCode
import no.slomic.smarthytte.issues.RecordingIssueReporter
import no.slomic.smarthytte.plugins.installRetry
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

private val START = Instant.parse("2026-11-15T10:00:00Z")
private const val DEVICE_URL = "https://auth.jlr-vcdp.com/realms/public/protocol/openid-connect/auth/device"

private class RecordedRequest(val url: String, val form: Parameters, val dpop: String?)

private fun deviceAuthorization(expiresIn: Long = 900, interval: Long = 5) = ok(
    """{"device_code":"device-secret","user_code":"ABCD-1234","verification_uri":"https://verify.example.test",""" +
        """"verification_uri_complete":"https://verify.example.test?code=ABCD-1234","expires_in":$expiresIn,""" +
        """"interval":$interval}""",
)

/** Everything a test needs: the service, its fakes and the scripted answers of the auth server, in order. */
private class AuthFixture(storedRefreshToken: String? = null, withRetry: Boolean = false) {
    val clock = MutableClock(START)
    val repository = FakeJlrTokenRepository(storedRefreshToken)
    val reporter = RecordingIssueReporter()
    val pauses = mutableListOf<Duration>()
    val requests = mutableListOf<RecordedRequest>()
    val script = ArrayDeque<Reply>()

    private val engine = MockEngine { request ->
        val form = (request.body as FormDataContent).formData
        requests += RecordedRequest(request.url.toString(), form, request.headers["DPoP"])
        val reply = script.removeFirstOrNull() ?: throw IllegalStateException("No scripted reply for ${request.url}")
        reply.failure?.let { throw it }
        respond(
            content = reply.body,
            status = reply.status,
            headers = headersOf(
                HttpHeaders.ContentType to listOf("application/json"),
                *reply.headers.map { (name, value) -> name to listOf(value) }.toTypedArray(),
            ),
        )
    }
    private val client = HttpClient(engine) { if (withRetry) installRetry(maxDelayMillis = 1, randomizationMillis = 0) }

    val service = JlrAuthService(
        httpClient = client,
        signer = testSigner(clock),
        tokenRepository = repository,
        issueReporter = reporter,
        clientId = "client-1",
        clock = clock,
        pause = {
            clock.current += it
            pauses += it
        },
    )
}

private fun RecordedRequest.assertionAudience() =
    jwtClaims(form["client_assertion"].orEmpty())["aud"]?.jsonPrimitive?.content

class JlrAuthServiceTest :
    ShouldSpec({
        context("device login, when no refresh token is stored") {
            should("report the link and code, poll until approved and store the refresh token") {
                val f = AuthFixture()
                f.script += deviceAuthorization()
                f.script += errorReply("authorization_pending")
                f.script += tokens(access = "access-1", refresh = "refresh-1")

                f.service.accessToken() shouldBe "access-1"

                f.pauses shouldContainExactly listOf(5.seconds, 5.seconds)
                f.repository.saved shouldContainExactly listOf("refresh-1")
                val report = f.reporter.reports.single()
                report.code shouldBe IssueCode.JLR_APPROVAL_REQUIRED
                report.key shouldBe "JLR_APPROVAL_REQUIRED"
                report.message shouldContain "https://verify.example.test?code=ABCD-1234"
                report.message shouldContain "ABCD-1234"
                report.message shouldNotContain "device-secret"
                // Approved: nobody has to approve any more
                f.reporter.resolved shouldContainExactly
                    listOf(IssueCode.JLR_APPROVAL_REQUIRED to "JLR_APPROVAL_REQUIRED")
            }

            should("send a client assertion with the token URL as audience and no DPoP proof to the device endpoint") {
                val f = AuthFixture()
                f.script += deviceAuthorization()
                f.script += tokens("access-1", "refresh-1")

                f.service.accessToken()

                val device = f.requests[0]
                device.url shouldBe DEVICE_URL
                device.dpop.shouldBeNull()
                device.assertionAudience() shouldBe TOKEN_URL
                device.form["client_id"] shouldBe "client-1"
                device.form["response_type"] shouldBe "device_code"
                device.form["code_challenge_method"] shouldBe "S256"
            }

            should("poll the token endpoint with the PKCE verifier, a DPoP proof and a client assertion") {
                val f = AuthFixture()
                f.script += deviceAuthorization()
                f.script += tokens("access-1", "refresh-1")

                f.service.accessToken()

                val poll = f.requests[1]
                poll.url shouldBe TOKEN_URL
                poll.form["grant_type"] shouldBe "urn:ietf:params:oauth:grant-type:device_code"
                poll.form["device_code"] shouldBe "device-secret"
                sha256Base64Url(poll.form["code_verifier"].orEmpty()) shouldBe f.requests[0].form["code_challenge"]
                poll.assertionAudience() shouldBe TOKEN_URL
                val proof = jwtClaims(poll.dpop.orEmpty())
                proof["htm"]?.jsonPrimitive?.content shouldBe "POST"
                proof["htu"]?.jsonPrimitive?.content shouldBe TOKEN_URL
                proof.containsKey("ath") shouldBe false
            }

            should("wait 5 seconds longer after slow_down") {
                val f = AuthFixture()
                f.script += deviceAuthorization()
                f.script += errorReply("slow_down")
                f.script += tokens("access-1", "refresh-1")

                f.service.accessToken()

                f.pauses shouldContainExactly listOf(5.seconds, 10.seconds)
            }

            should("fail when the owner denies, and not start another login for an hour") {
                val f = AuthFixture()
                f.script += deviceAuthorization()
                f.script += errorReply("access_denied")

                shouldThrow<JlrAuthException> { f.service.accessToken() }
                val requestsAfterFirst = f.requests.size
                f.reporter.resolved shouldContainExactly
                    listOf(IssueCode.JLR_APPROVAL_REQUIRED to "JLR_APPROVAL_REQUIRED")

                shouldThrow<JlrAuthException> { f.service.accessToken() }.message shouldContain "next one starts at"
                f.requests shouldHaveSize requestsAfterFirst
                // The cooldown asks nobody to approve, so nothing is reported or resolved again
                f.reporter.reports shouldHaveSize 1
                f.reporter.resolved shouldHaveSize 1

                f.clock.current += 1.hours + 1.seconds
                f.script += deviceAuthorization()
                f.script += tokens("access-2", "refresh-2")
                f.service.accessToken() shouldBe "access-2"
            }

            should("fail when not approved before the code expires") {
                val f = AuthFixture()
                f.script += deviceAuthorization(expiresIn = 12, interval = 5)
                repeat(3) { f.script += errorReply("authorization_pending") }

                shouldThrow<JlrAuthException> { f.service.accessToken() }.message shouldContain "not approved in time"
                f.requests shouldHaveSize 4
                f.reporter.resolved shouldContainExactly
                    listOf(IssueCode.JLR_APPROVAL_REQUIRED to "JLR_APPROVAL_REQUIRED")
            }

            should("keep the approval issue open when the server fails while polling") {
                val f = AuthFixture()
                f.script += deviceAuthorization()
                f.script += Reply(HttpStatusCode.InternalServerError, "oops")

                shouldThrow<JlrRequestException> { f.service.accessToken() }

                f.reporter.reports.single().code shouldBe IssueCode.JLR_APPROVAL_REQUIRED
                f.reporter.resolved.shouldBeEmpty()
            }

            should("fail with a request exception when the device endpoint is down") {
                val f = AuthFixture()
                f.script += Reply(HttpStatusCode.InternalServerError, "oops")

                shouldThrow<JlrRequestException> { f.service.accessToken() }
                f.reporter.reports.shouldBeEmpty()
            }
        }

        context("renewing with the refresh token") {
            should("cache the access token until 60 seconds before it expires") {
                val f = AuthFixture(storedRefreshToken = "refresh-0")
                f.script += tokens("access-1", "refresh-1", expiresIn = 300)

                f.service.accessToken() shouldBe "access-1"
                f.clock.current += 239.seconds
                f.service.accessToken() shouldBe "access-1"
                f.requests shouldHaveSize 1

                f.clock.current += 2.seconds
                f.script += tokens("access-2", "refresh-2")
                f.service.accessToken() shouldBe "access-2"
                f.requests shouldHaveSize 2
            }

            should("send the stored refresh token with a DPoP proof and persist the rotated one") {
                val f = AuthFixture(storedRefreshToken = "refresh-0")
                f.script += tokens("access-1", "refresh-1")

                f.service.accessToken()

                val request = f.requests.single()
                request.form["grant_type"] shouldBe "refresh_token"
                request.form["refresh_token"] shouldBe "refresh-0"
                request.dpop.orEmpty().isNotEmpty() shouldBe true
                f.repository.saved shouldContainExactly listOf("refresh-1")
                f.reporter.reports.shouldBeEmpty()
                f.reporter.resolved.shouldBeEmpty()
            }

            should("keep the old refresh token when the answer has no new one") {
                val f = AuthFixture(storedRefreshToken = "refresh-0")
                f.script += tokens("access-1", refresh = null)

                f.service.accessToken()
                f.clock.current += 10.minutes
                f.script += tokens("access-2", refresh = null)
                f.service.accessToken()

                f.repository.saved.shouldBeEmpty()
                f.requests.map { it.form["refresh_token"] } shouldContainExactly listOf("refresh-0", "refresh-0")
            }

            should("use the rotated token from memory when storing it failed") {
                val f = AuthFixture(storedRefreshToken = "refresh-0")
                f.repository.failNextSave = true
                f.script += tokens("access-1", "refresh-1")
                shouldThrow<IllegalStateException> { f.service.accessToken() }

                f.script += tokens("access-2", "refresh-2")
                f.service.accessToken() shouldBe "access-2"

                f.requests[1].form["refresh_token"] shouldBe "refresh-1"
                f.repository.saved shouldContainExactly listOf("refresh-2")
            }

            should("renew once when two callers ask at the same time") {
                val f = AuthFixture(storedRefreshToken = "refresh-0")
                f.script += tokens("access-1", "refresh-1")

                val answers = coroutineScope { List(2) { async { f.service.accessToken() } }.awaitAll() }

                answers shouldContainExactly listOf("access-1", "access-1")
                f.requests shouldHaveSize 1
            }

            should("forget a rejected refresh token (invalid_grant) and start a device login next time") {
                val f = AuthFixture(storedRefreshToken = "refresh-0")
                f.script += errorReply("invalid_grant")

                shouldThrow<JlrAuthException> { f.service.accessToken() }

                f.repository.stored.shouldBeNull()
                f.repository.deleteCount shouldBe 1
                f.script += deviceAuthorization()
                f.script += tokens("access-2", "refresh-2")
                f.service.accessToken() shouldBe "access-2"
                f.reporter.reports.single().code shouldBe IssueCode.JLR_APPROVAL_REQUIRED
            }

            should("keep the refresh token when the client is rejected, it is a configuration problem") {
                listOf("invalid_client", "unauthorized_client").forEach { code ->
                    val f = AuthFixture(storedRefreshToken = "refresh-0")
                    f.script += errorReply(code, HttpStatusCode.Unauthorized)

                    shouldThrow<JlrAuthException> { f.service.accessToken() }.message shouldContain code

                    f.repository.stored shouldBe "refresh-0"
                    f.requests shouldHaveSize 1
                }
            }

            should("throw a request exception on a server error, without touching the stored token") {
                val f = AuthFixture(storedRefreshToken = "refresh-0")
                f.script += Reply(HttpStatusCode.BadGateway, "bad gateway")

                shouldThrow<JlrRequestException> { f.service.accessToken() }

                f.repository.stored shouldBe "refresh-0"
            }

            should("never put tokens in an exception message") {
                val f = AuthFixture(storedRefreshToken = "refresh-secret")
                f.script += errorReply("invalid_grant")

                val message = shouldThrow<JlrAuthException> { f.service.accessToken() }.message.orEmpty()

                message shouldNotContain "refresh-secret"
            }
        }

        context("failures that are not the owner's decision") {
            should("not start the one hour cooldown when the server fails while polling") {
                val f = AuthFixture()
                f.script += deviceAuthorization()
                f.script += Reply(HttpStatusCode.ServiceUnavailable, "down")

                shouldThrow<JlrRequestException> { f.service.accessToken() }

                f.script += deviceAuthorization()
                f.script += tokens("access-2", "refresh-2")
                f.service.accessToken() shouldBe "access-2"
            }

            should("not put the response body in the exception when it cannot be read") {
                val f = AuthFixture(storedRefreshToken = "refresh-0")
                f.script += ok("""{"access_token":"access-secret","refresh_token":"refresh-secret","expires_in":"x"}""")

                val error = shouldThrow<JlrRequestException> { f.service.accessToken() }

                error.message.orEmpty() shouldNotContain "access-secret"
                error.cause.shouldBeNull()
            }

            should("not retry a refresh after a network failure, the token may be spent") {
                val f = AuthFixture(storedRefreshToken = "refresh-0", withRetry = true)
                f.script += Reply(HttpStatusCode.OK, "", failure = java.io.IOException("connection reset"))

                shouldThrow<java.io.IOException> { f.service.accessToken() }

                f.requests shouldHaveSize 1
                f.repository.stored shouldBe "refresh-0"
            }

            should("save the rotated token later when the first save failed and the next answer repeats it") {
                val f = AuthFixture(storedRefreshToken = "refresh-0")
                f.repository.failNextSave = true
                f.script += tokens("access-1", "refresh-1")
                shouldThrow<IllegalStateException> { f.service.accessToken() }

                f.script += tokens("access-2", refresh = null)
                f.service.accessToken()

                f.repository.saved shouldContainExactly listOf("refresh-1")
            }
        }

        context("DPoP nonce") {
            should("retry once with the nonce, a new proof and a new client assertion") {
                val f = AuthFixture(storedRefreshToken = "refresh-0")
                f.script += errorReply("use_dpop_nonce", nonce = "nonce-1")
                f.script += tokens("access-1", "refresh-1")

                f.service.accessToken() shouldBe "access-1"

                val (first, second) = f.requests
                jwtClaims(first.dpop.orEmpty()).containsKey("nonce") shouldBe false
                jwtClaims(second.dpop.orEmpty())["nonce"]?.jsonPrimitive?.content shouldBe "nonce-1"
                (jwtClaims(first.dpop.orEmpty())["jti"] == jwtClaims(second.dpop.orEmpty())["jti"]) shouldBe false
                (first.form["client_assertion"] == second.form["client_assertion"]) shouldBe false
            }

            should("retry only once, then report the failure") {
                val f = AuthFixture(storedRefreshToken = "refresh-0")
                f.script += errorReply("use_dpop_nonce", nonce = "nonce-1")
                f.script += errorReply("use_dpop_nonce", nonce = "nonce-2")

                shouldThrow<JlrRequestException> { f.service.accessToken() }

                f.requests shouldHaveSize 2
            }

            should("not retry on another error that carries a nonce header") {
                val f = AuthFixture(storedRefreshToken = "refresh-0")
                f.script += errorReply("invalid_request", nonce = "nonce-1")

                shouldThrow<JlrRequestException> { f.service.accessToken() }

                f.requests shouldHaveSize 1
            }

            should("add a remembered nonce and the token hash to API proofs") {
                val f = AuthFixture()
                f.service.rememberDpopNonce("api-nonce")

                val claims = jwtClaims(f.service.apiDpopProof("GET", "https://api.example.test/v1/x?page=0", "tok"))

                claims["nonce"]?.jsonPrimitive?.content shouldBe "api-nonce"
                claims["ath"]?.jsonPrimitive?.content shouldBe sha256Base64Url("tok")
                claims["htu"]?.jsonPrimitive?.content shouldBe "https://api.example.test/v1/x"
            }
        }

        context("after the API rejected the access token") {
            should("renew, but only once for callers that were rejected with the same token") {
                val f = AuthFixture(storedRefreshToken = "refresh-0")
                f.script += tokens("access-1", "refresh-1")
                f.service.accessToken()

                f.script += tokens("access-2", "refresh-2")
                val first = f.service.accessTokenAfterRejection("access-1")
                val second = f.service.accessTokenAfterRejection("access-1")

                first shouldBe "access-2"
                second shouldBe "access-2"
                f.requests shouldHaveSize 2
            }
        }

        context("with the retrying HTTP client") {
            should("sign every retry of a token request again, so no jti is sent twice") {
                val f = AuthFixture(storedRefreshToken = "refresh-0", withRetry = true)
                f.script += Reply(HttpStatusCode.InternalServerError, "oops")
                f.script += tokens("access-1", "refresh-1")

                f.service.accessToken() shouldBe "access-1"

                val (first, second) = f.requests
                (first.form["client_assertion"] == second.form["client_assertion"]) shouldBe false
                (jwtClaims(first.dpop.orEmpty())["jti"] == jwtClaims(second.dpop.orEmpty())["jti"]) shouldBe false
            }
        }
    })
