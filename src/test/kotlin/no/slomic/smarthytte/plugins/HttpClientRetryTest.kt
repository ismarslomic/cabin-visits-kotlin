package no.slomic.smarthytte.plugins

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.io.IOException
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

class HttpClientRetryTest :
    ShouldSpec({
        fun client(vararg statuses: HttpStatusCode, onRequest: (Int) -> Unit = {}): Pair<HttpClient, () -> Int> {
            var calls = 0
            val engine = MockEngine {
                val status = statuses[minOf(calls, statuses.lastIndex)]
                calls++
                onRequest(calls)
                respond(
                    content = "",
                    status = status,
                    headers = if (status == HttpStatusCode.TooManyRequests) {
                        headersOf(HttpHeaders.RetryAfter, "1")
                    } else {
                        headersOf()
                    },
                )
            }
            return HttpClient(engine) { installRetry(maxDelayMillis = 1, randomizationMillis = 0) } to { calls }
        }

        should("retry a server error and return the next answer") {
            val (client, calls) = client(HttpStatusCode.InternalServerError, HttpStatusCode.OK)

            client.get("https://example.test/x").status shouldBe HttpStatusCode.OK
            calls() shouldBe 2
        }

        should("retry 429 and honour Retry-After") {
            val (client, calls) = client(HttpStatusCode.TooManyRequests, HttpStatusCode.OK)

            val started = TimeSource.Monotonic.markNow()
            client.get("https://example.test/x").status shouldBe HttpStatusCode.OK

            calls() shouldBe 2
            // The back-off alone is capped at 1 ms in this test, so only the header can explain the wait
            (started.elapsedNow() >= 900.milliseconds) shouldBe true
        }

        should("give up after 3 retries and return the last answer") {
            val (client, calls) = client(HttpStatusCode.BadGateway)

            client.get("https://example.test/x").status shouldBe HttpStatusCode.BadGateway
            calls() shouldBe 4
        }

        should("not retry a client error") {
            val (client, calls) = client(HttpStatusCode.Forbidden)

            client.get("https://example.test/x").status shouldBe HttpStatusCode.Forbidden
            calls() shouldBe 1
        }

        should("retry a network failure") {
            var calls = 0
            val engine = MockEngine {
                calls++
                if (calls == 1) throw IOException("connection reset")
                respond("", HttpStatusCode.OK)
            }
            val client = HttpClient(engine) { installRetry(maxDelayMillis = 1, randomizationMillis = 0) }

            client.get("https://example.test/x").status shouldBe HttpStatusCode.OK
            calls shouldBe 2
        }

        should("call the request signer again before each retry, not for the first attempt twice") {
            var signed = 0
            val (client, _) = client(HttpStatusCode.InternalServerError, HttpStatusCode.OK)

            client.get("https://example.test/x") {
                attributes.put(SignRequest) { request ->
                    signed++
                    request.header("DPoP", "proof-$signed")
                }
            }

            signed shouldBe 1
        }
    })
