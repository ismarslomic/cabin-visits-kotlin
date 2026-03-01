package no.slomic.smarthytte.skistats

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import no.slomic.smarthytte.common.PersistenceResult
import no.slomic.smarthytte.properties.CoreSkiStatsProperties
import java.net.ServerSocket
import kotlin.time.Instant

class SkiStatsApiClientTest :
    FunSpec({
        test("loadTokens uses repository tokens and sends Bearer on baseUrl") {
            val (server, port, authHeaderStore) = startServer { authHeader ->
                if (authHeader == "Bearer access-1") {
                    HttpStatusCode.OK
                } else {
                    HttpStatusCode.Unauthorized
                }
            }

            val coreProps = coreProps("http://localhost:$port")
            val repo = mockk<SkiStatsRepository>()
            val authClient = mockk<SkiStatsAuthClient>(relaxed = true)

            coEvery { repo.tokensByProfile("p1") } returns SkiStatsTokens("access-1", "refresh-1")

            val client = createSkiStatsApiClient(coreProps, repo, "p1", authClient)
            try {
                val response = client.get("${coreProps.baseUrl}/protected")
                response.status shouldBe HttpStatusCode.OK
                authHeaderStore.last() shouldBe "Bearer access-1"
            } finally {
                client.close()
                server.stop(0, 0)
            }
        }

        test("refreshTokens refreshes on 401 and stores updated tokens") {
            val (server, port, authHeaderStore) = startServerWithRefresh()
            val coreProps = coreProps("http://localhost:$port")

            val repo = mockk<SkiStatsRepository>()
            val authClient = mockk<SkiStatsAuthClient>()

            coEvery { repo.tokensByProfile("p1") } returns SkiStatsTokens("access-old", "refresh-old")
            coEvery { repo.addOrUpdateTokens(any(), any()) } returns PersistenceResult.UPDATED

            val expiresAt = Instant.fromEpochSeconds(123456)
            coEvery { authClient.refreshGrant("refresh-old") } returns OAuthTokenResponse(
                refreshToken = "refresh-new",
                accessToken = "access-new",
                expiresAtUtc = expiresAt,
                userId = null,
                clientId = null,
                environment = null,
                agentId = null,
                issuedAtUtc = null,
                tokenType = null,
                expiresIn = null,
                scope = null,
            )

            val client = createSkiStatsApiClient(coreProps, repo, "p1", authClient)
            try {
                val response = client.get("${coreProps.baseUrl}/protected")
                response.status shouldBe HttpStatusCode.OK

                authHeaderStore.first() shouldBe "Bearer access-old"
                authHeaderStore.last() shouldBe "Bearer access-new"

                coVerify(exactly = 1) { authClient.refreshGrant("refresh-old") }
                val stored = slot<SkiStatsTokens>()
                coVerify(exactly = 1) { repo.addOrUpdateTokens("p1", capture(stored)) }
                stored.captured.accessToken shouldBe "access-new"
                stored.captured.refreshToken shouldBe "refresh-new"
                stored.captured.expiresAtEpochSeconds shouldBe 123456L
            } finally {
                client.close()
                server.stop(0, 0)
            }
        }

        test("sendWithoutRequest does not attach Authorization for non-baseUrl") {
            val (server1, port1, _) = startServer { HttpStatusCode.OK }
            val (server2, port2, authHeaderStore2) = startServer { authHeader ->
                if (authHeader == null) HttpStatusCode.OK else HttpStatusCode.BadRequest
            }

            val coreProps = coreProps("http://localhost:$port1")
            val repo = mockk<SkiStatsRepository>()
            val authClient = mockk<SkiStatsAuthClient>(relaxed = true)

            coEvery { repo.tokensByProfile("p1") } returns SkiStatsTokens("access-1", "refresh-1")

            val client = createSkiStatsApiClient(coreProps, repo, "p1", authClient)
            try {
                val response = client.get("http://localhost:$port2/protected")
                response.status shouldBe HttpStatusCode.OK
                authHeaderStore2.last() shouldBe null
            } finally {
                client.close()
                server1.stop(0, 0)
                server2.stop(0, 0)
            }
        }
    })

private fun startServer(
    statusForAuth: (String?) -> HttpStatusCode,
): Triple<EmbeddedServer<*, *>, Int, MutableList<String?>> {
    val port = ServerSocket(0).use { it.localPort }
    val authHeaders = mutableListOf<String?>()
    val server = embeddedServer(CIO, port = port) {
        routing {
            get("/protected") {
                val authHeader = call.request.headers[HttpHeaders.Authorization]
                authHeaders.add(authHeader)
                val status = statusForAuth(authHeader)
                if (status == HttpStatusCode.Unauthorized) {
                    call.response.header(HttpHeaders.WWWAuthenticate, "Bearer")
                }
                call.respond(status, "")
            }
        }
    }
    server.start()
    return Triple(server, port, authHeaders)
}

private fun startServerWithRefresh(): Triple<EmbeddedServer<*, *>, Int, MutableList<String?>> {
    val port = ServerSocket(0).use { it.localPort }
    val authHeaders = mutableListOf<String?>()
    var requestCount = 0

    val server = embeddedServer(CIO, port = port) {
        routing {
            get("/protected") {
                val authHeader = call.request.headers[HttpHeaders.Authorization]
                authHeaders.add(authHeader)
                requestCount += 1

                if (requestCount == 1) {
                    call.response.header(HttpHeaders.WWWAuthenticate, "Bearer")
                    call.respond(HttpStatusCode.Unauthorized, "")
                } else if (authHeader == "Bearer access-new") {
                    call.respond(HttpStatusCode.OK, "")
                } else {
                    call.respond(HttpStatusCode.Unauthorized, "")
                }
            }
        }
    }

    server.start()
    return Triple(server, port, authHeaders)
}

private fun coreProps(baseUrl: String) = CoreSkiStatsProperties(
    baseUrl = baseUrl,
    authPath = "/oauth/token",
    seasonStatsPath = "/season",
    appInstanceId = "app-instance",
    appPlatform = "ios",
    apiKey = "api-key",
    appVersion = "1.0.0",
    cookie = "cookie",
    userAgent = "user-agent",
)
