package no.slomic.smarthytte.vehicletrips.jlr

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.string.shouldStartWith
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteChannel
import kotlinx.serialization.json.jsonPrimitive
import no.slomic.smarthytte.common.issues.RecordingIssueReporter
import no.slomic.smarthytte.plugins.installRetry
import java.io.IOException
import kotlin.time.Instant

private val NOW = Instant.parse("2026-11-15T10:00:00Z")
private const val VIN = "SAL0000000VIN0001"
private const val JOURNEYS_URL = "https://data.jlr-vcdp.com/v1/vehicles/$VIN/journeys"

private class ApiRequest(
    val url: String,
    val query: Map<String, String>,
    val authorization: String?,
    val dpopHeaders: List<String>,
) {
    val dpop: String get() = dpopHeaders.single()
}

private fun journey(id: String) = """
    {"journeyId":"$id","startTime":"2026-11-14T08:00:00Z","endTime":"2026-11-14T10:30:00Z","duration":9000.0,
     "distance":{"value":150.5,"unit":"km"},"averageSpeed":{"value":60.2,"unit":"km/h"},
     "startLocation":{"latitude":59.1,"longitude":10.1},"endLocation":{"latitude":60.2,"longitude":8.2}}
""".trimIndent()

private fun journeyPage(ids: List<String>, currentPage: Int = 0, totalPages: Int = 1) = ok(
    """{"items":[${ids.joinToString(",") { journey(it) }}],"page":{"totalElements":${ids.size},""" +
        """"totalPages":$totalPages,"currentPage":$currentPage,"pageSize":100}}""",
)

private fun challenge(nonce: String) = Reply(
    HttpStatusCode.Unauthorized,
    """{"error":"use_dpop_nonce"}""",
    listOf("DPoP-Nonce" to nonce, "WWW-Authenticate" to """DPoP error="use_dpop_nonce""""),
)

/** The journey client with a real [JlrAuthService], and a fake server for both the auth server and the API. */
private class ApiFixture(withRetry: Boolean = false) {
    val clock = MutableClock(NOW)
    val authScript = ArrayDeque<Reply>()
    val apiScript = ArrayDeque<Reply>()
    val apiRequests = mutableListOf<ApiRequest>()
    val tokenRequests = mutableListOf<String>()

    private val engine = MockEngine { request ->
        val isAuth = request.url.host == "auth.jlr-vcdp.com"
        val reply = (if (isAuth) authScript else apiScript).removeFirstOrNull()
            ?: throw IllegalStateException("No scripted reply for ${request.url}")
        if (isAuth) {
            tokenRequests += request.url.toString()
        } else {
            apiRequests += ApiRequest(
                url = request.url.toString().substringBefore('?'),
                query = request.url.parameters.entries().associate { it.key to it.value.first() },
                authorization = request.headers[HttpHeaders.Authorization],
                dpopHeaders = request.headers.getAll("DPoP").orEmpty(),
            )
        }
        reply.failure?.let { throw it }
        val headers = headersOf(
            HttpHeaders.ContentType to listOf("application/json"),
            *reply.headers.map { (name, value) -> name to listOf(value) }.toTypedArray(),
        )
        val failure = reply.bodyFailure
        if (failure != null) {
            // The headers arrive, then the connection drops while the body is read
            respond(ByteChannel().apply { cancel(failure) }, reply.status, headers)
        } else {
            respond(reply.body, reply.status, headers)
        }
    }
    private val client = HttpClient(engine) { if (withRetry) installRetry(maxDelayMillis = 1, randomizationMillis = 0) }

    val authService = JlrAuthService(
        httpClient = client,
        signer = testSigner(clock),
        tokenRepository = FakeJlrTokenRepository("refresh-0"),
        issueReporter = RecordingIssueReporter(),
        clientId = "client-1",
        clock = clock,
        pause = { },
    )
    val journeyClient = JlrJourneyClient(client, authService, VIN)

    init {
        authScript += tokens("access-1", "refresh-1")
    }
}

private val FROM = Instant.parse("2026-11-01T00:00:00Z")
private val TO = Instant.parse("2026-11-15T00:00:00Z")

class JlrJourneyClientTest :
    ShouldSpec({
        context("request") {
            should("send the token, a DPoP proof for this request and the period and page size as query") {
                val f = ApiFixture()
                f.apiScript += journeyPage(listOf("1"))

                f.journeyClient.fetchJourneys(FROM, TO)

                val request = f.apiRequests.single()
                request.url shouldBe JOURNEYS_URL
                request.authorization shouldBe "DPoP access-1"
                request.query shouldBe mapOf(
                    "page" to "0",
                    "size" to "100",
                    "from" to "2026-11-01T00:00:00Z",
                    "to" to "2026-11-15T00:00:00Z",
                )
                val proof = jwtClaims(request.dpop)
                proof["htm"]?.jsonPrimitive?.content shouldBe "GET"
                proof["htu"]?.jsonPrimitive?.content shouldBe JOURNEYS_URL
                proof["ath"]?.jsonPrimitive?.content shouldBe sha256Base64Url("access-1")
            }

            should("leave out `to` when it is not given, and never sort") {
                val f = ApiFixture()
                f.apiScript += journeyPage(listOf("1"))

                f.journeyClient.fetchJourneys(FROM)

                f.apiRequests.single().query.keys shouldBe setOf("page", "size", "from")
            }
        }

        context("response") {
            should("map the journeys of a page") {
                val f = ApiFixture()
                f.apiScript += journeyPage(listOf("8764829"))

                val journeys = f.journeyClient.fetchJourneys(FROM, TO)

                journeys.single().journeyId shouldBe "8764829"
                journeys.single().distance.value shouldBe 150.5
                journeys.single().endLocation.longitude shouldBe 8.2
            }

            should("read every page, in order, with one token") {
                val f = ApiFixture()
                f.apiScript += journeyPage(listOf("1", "2"), currentPage = 0, totalPages = 3)
                f.apiScript += journeyPage(listOf("3"), currentPage = 1, totalPages = 3)
                f.apiScript += journeyPage(listOf("4"), currentPage = 2, totalPages = 3)

                val journeys = f.journeyClient.fetchJourneys(FROM, TO)

                journeys.map { it.journeyId } shouldContainExactly listOf("1", "2", "3", "4")
                f.apiRequests.map { it.query["page"] } shouldContainExactly listOf("0", "1", "2")
                f.tokenRequests shouldHaveSize 1
            }

            should("return an empty list for a period without journeys") {
                val f = ApiFixture()
                f.apiScript += journeyPage(emptyList(), totalPages = 0)

                f.journeyClient.fetchJourneys(FROM, TO) shouldBe emptyList()
                f.apiRequests shouldHaveSize 1
            }
        }

        context("an expired access token (403 forbidden)") {
            should("renew the token once and ask again with the new one") {
                val f = ApiFixture()
                f.apiScript += errorReply("forbidden", HttpStatusCode.Forbidden)
                f.apiScript += journeyPage(listOf("1"))
                f.authScript += tokens("access-2", "refresh-2")

                f.journeyClient.fetchJourneys(FROM, TO) shouldHaveSize 1

                f.apiRequests.map { it.authorization } shouldContainExactly listOf("DPoP access-1", "DPoP access-2")
                jwtClaims(f.apiRequests[1].dpop)["ath"]?.jsonPrimitive?.content shouldBe sha256Base64Url("access-2")
            }

            should("give up when the new token is rejected too, after one renewal") {
                val f = ApiFixture()
                f.apiScript += errorReply("forbidden", HttpStatusCode.Forbidden)
                f.apiScript += errorReply("forbidden", HttpStatusCode.Forbidden)
                f.authScript += tokens("access-2", "refresh-2")

                shouldThrow<JlrRequestException> { f.journeyClient.fetchJourneys(FROM, TO) }
                    .message shouldBe "JLR journeys request failed: status=403"

                f.apiRequests shouldHaveSize 2
                f.tokenRequests shouldHaveSize 2
            }

            should("not renew for a 403 that is not `forbidden`") {
                val f = ApiFixture()
                f.apiScript += errorReply("insufficient_scope", HttpStatusCode.Forbidden)

                shouldThrow<JlrRequestException> { f.journeyClient.fetchJourneys(FROM, TO) }

                f.tokenRequests shouldHaveSize 1
            }
        }

        context("DPoP nonce") {
            should("ask again once with the nonce, a new proof and the same token") {
                val f = ApiFixture()
                f.apiScript += challenge("api-nonce")
                f.apiScript += journeyPage(listOf("1"))

                f.journeyClient.fetchJourneys(FROM, TO) shouldHaveSize 1

                val (first, second) = f.apiRequests
                jwtClaims(first.dpop).containsKey("nonce") shouldBe false
                jwtClaims(second.dpop)["nonce"]?.jsonPrimitive?.content shouldBe "api-nonce"
                (jwtClaims(first.dpop)["jti"] == jwtClaims(second.dpop)["jti"]) shouldBe false
                second.authorization shouldBe "DPoP access-1"
            }

            should("not ask a third time when the server keeps asking") {
                val f = ApiFixture()
                f.apiScript += challenge("n1")
                f.apiScript += challenge("n2")

                shouldThrow<JlrRequestException> { f.journeyClient.fetchJourneys(FROM, TO) }
                    .message shouldBe "JLR journeys request failed: status=401"

                f.apiRequests shouldHaveSize 2
            }

            should("not treat a plain 401 with a nonce header as a challenge") {
                val f = ApiFixture()
                f.apiScript += Reply(HttpStatusCode.Unauthorized, "", listOf("DPoP-Nonce" to "n1"))

                shouldThrow<JlrRequestException> { f.journeyClient.fetchJourneys(FROM, TO) }

                f.apiRequests shouldHaveSize 1
            }
        }

        context("paging limits") {
            should("stop at an empty page even when totalPages says more") {
                val f = ApiFixture()
                f.apiScript += journeyPage(listOf("1"), currentPage = 0, totalPages = 5)
                f.apiScript += journeyPage(emptyList(), currentPage = 1, totalPages = 5)

                f.journeyClient.fetchJourneys(FROM, TO) shouldHaveSize 1

                f.apiRequests shouldHaveSize 2
            }

            should("give up when the server keeps sending pages") {
                val f = ApiFixture()
                repeat(1000) { f.apiScript += journeyPage(listOf("$it"), currentPage = it, totalPages = 2_000_000_000) }

                shouldThrow<JlrRequestException> { f.journeyClient.fetchJourneys(FROM, TO) }
                    .message shouldBe "JLR journeys has more than 1000 pages"

                f.apiRequests shouldHaveSize 1000
            }
        }

        context("renewal and nonce together, per page") {
            should("accept a new nonce challenge after the token was renewed") {
                val f = ApiFixture()
                f.apiScript += challenge("n1")
                f.apiScript += errorReply("forbidden", HttpStatusCode.Forbidden)
                f.apiScript += challenge("n2")
                f.apiScript += journeyPage(listOf("1"))
                f.authScript += tokens("access-2", "refresh-2")

                f.journeyClient.fetchJourneys(FROM, TO) shouldHaveSize 1

                f.apiRequests.map { it.authorization } shouldContainExactly
                    listOf("DPoP access-1", "DPoP access-1", "DPoP access-2", "DPoP access-2")
                jwtClaims(f.apiRequests[3].dpop)["nonce"]?.jsonPrimitive?.content shouldBe "n2"
            }

            should("use the renewed token for the next page, without another token request") {
                val f = ApiFixture()
                f.apiScript += errorReply("forbidden", HttpStatusCode.Forbidden)
                f.apiScript += journeyPage(listOf("1"), currentPage = 0, totalPages = 2)
                f.apiScript += journeyPage(listOf("2"), currentPage = 1, totalPages = 2)
                f.authScript += tokens("access-2", "refresh-2")

                f.journeyClient.fetchJourneys(FROM, TO) shouldHaveSize 2

                f.apiRequests.map { it.authorization }.last() shouldBe "DPoP access-2"
                f.tokenRequests shouldHaveSize 2
            }

            should("allow one renewal on every page") {
                val f = ApiFixture()
                f.apiScript += journeyPage(listOf("1"), currentPage = 0, totalPages = 2)
                f.apiScript += errorReply("forbidden", HttpStatusCode.Forbidden)
                f.apiScript += journeyPage(listOf("2"), currentPage = 1, totalPages = 2)
                f.authScript += tokens("access-2", "refresh-2")

                f.journeyClient.fetchJourneys(FROM, TO) shouldHaveSize 2

                f.tokenRequests shouldHaveSize 2
            }
        }

        context("failures") {
            should("turn a connection that drops while the body is read into a request exception") {
                val f = ApiFixture()
                f.apiScript += Reply(
                    HttpStatusCode.OK,
                    "",
                    bodyFailure = IOException("Connection reset [url=$JOURNEYS_URL]"),
                )

                val error = shouldThrow<JlrRequestException> { f.journeyClient.fetchJourneys(FROM, TO) }

                error.message.orEmpty() shouldStartWith "JLR journeys request failed: "
                error.message.orEmpty() shouldNotContain VIN
                error.cause.shouldBeNull()
            }

            should("retry 429 and server errors, with a new proof each time and a single DPoP header") {
                val f = ApiFixture(withRetry = true)
                f.apiScript += Reply(HttpStatusCode.TooManyRequests, "", listOf("Retry-After" to "0"))
                f.apiScript += Reply(HttpStatusCode.BadGateway, "")
                f.apiScript += journeyPage(listOf("1"))

                f.journeyClient.fetchJourneys(FROM, TO) shouldHaveSize 1

                f.apiRequests shouldHaveSize 3
                f.apiRequests.map { it.dpopHeaders.size }.toSet() shouldBe setOf(1)
                f.apiRequests.map { jwtClaims(it.dpop)["jti"]?.jsonPrimitive?.content }.toSet() shouldHaveSize 3
            }

            should("throw a request exception when the server keeps failing, without the VIN or token in the message") {
                val f = ApiFixture(withRetry = true)
                repeat(4) { f.apiScript += Reply(HttpStatusCode.ServiceUnavailable, "") }

                val message = shouldThrow<JlrRequestException> { f.journeyClient.fetchJourneys(FROM, TO) }
                    .message.orEmpty()

                message shouldBe "JLR journeys request failed: status=503"
                message shouldNotContain VIN
                message shouldNotContain "access-1"
            }

            should("throw a request exception for a status that is not expected") {
                val f = ApiFixture()
                f.apiScript += Reply(HttpStatusCode.NotFound, "")

                shouldThrow<JlrRequestException> { f.journeyClient.fetchJourneys(FROM, TO) }
                    .message shouldContain "status=404"
            }

            should("turn a network failure into a request exception without the URL (and VIN) in it") {
                val f = ApiFixture()
                f.apiScript += Reply(
                    HttpStatusCode.OK,
                    "",
                    failure = IOException("Request timeout has expired [url=$JOURNEYS_URL, request_timeout=30000 ms]"),
                )

                val error = shouldThrow<JlrRequestException> { f.journeyClient.fetchJourneys(FROM, TO) }

                error.message.orEmpty() shouldStartWith "JLR journeys request failed: "
                error.message.orEmpty() shouldNotContain VIN
                error.cause.shouldBeNull()
            }

            should("throw a request exception, without the body in it, when the body cannot be read") {
                val f = ApiFixture()
                f.apiScript += ok("""{"items":"not a list","secret":"body-secret"}""")

                val error = shouldThrow<JlrRequestException> { f.journeyClient.fetchJourneys(FROM, TO) }

                error.message shouldBe "JLR journeys response could not be read"
                error.cause.shouldBeNull()
            }

            should("let a failed login through as JlrAuthException") {
                val f = ApiFixture()
                f.authScript.clear()
                f.authScript += errorReply("invalid_client", HttpStatusCode.Unauthorized)

                shouldThrow<JlrAuthException> { f.journeyClient.fetchJourneys(FROM, TO) }

                f.apiRequests shouldHaveSize 0
            }
        }
    })
