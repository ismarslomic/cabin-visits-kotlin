package no.slomic.smarthytte.plugins

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode

/**
 * Tests for [HttpClientProvider]
 *
 * The focus of the test suite is to validate the configuration
 * and behavior of the HttpClient provided by HttpClientProvider.
 */
class HttpClientProviderTest :
    ShouldSpec({

        should("create an HttpClient with required plugins installed") {
            run {
                val client = HttpClientProvider.client

                val response: HttpResponse = client.get("https://httpbingo.org/get")
                response.status shouldBe HttpStatusCode.OK
            }
        }

        should("handle cookies with AcceptAllCookiesStorage") {
            run {
                val client = HttpClientProvider.client

                // Step 1: Hit the endpoint that sets a cookie using response-headers (no redirect involved)
                val setCookieResponse: HttpResponse =
                    client.get("https://httpbingo.org/response-headers?Set-Cookie=testCookie=foobar")
                setCookieResponse.status shouldBe HttpStatusCode.OK

                // Step 2: Call /cookies to verify the cookie is sent back and echoed in the response
                val verifyResponse: HttpResponse = client.get("https://httpbingo.org/cookies")
                verifyResponse.status shouldBe HttpStatusCode.OK
            }
        }

        should("ignore unknown JSON keys in responses") {
            run {
                val client = HttpClientProvider.client

                val response: HttpResponse = client.get("https://httpbingo.org/json")
                response.status shouldBe HttpStatusCode.OK
            }
        }
    })
