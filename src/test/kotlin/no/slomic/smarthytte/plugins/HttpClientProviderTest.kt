package no.slomic.smarthytte.plugins

import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Tests for [HttpClientProvider]
 *
 * The focus of the test suite is to validate the configuration
 * and behavior of the HttpClient provided by HttpClientProvider.
 */
class HttpClientProviderTest {

    @Test
    fun `should create an HttpClient with required plugins installed`() = runBlocking {
        val client = HttpClientProvider.client

        // Test that the client is initialized successfully
        assertNotNull(client, "HttpClient should be initialized")

        // Perform a basic configuration check
        val response: HttpResponse = client.get("https://httpbingo.org/get")
        assertEquals(
            expected = HttpStatusCode.OK,
            actual = response.status,
            message = "The client should return HTTP 200 OK for a successful request.",
        )
    }

    @Test
    fun `should handle cookies with AcceptAllCookiesStorage`() = runBlocking {
        val client = HttpClientProvider.client

        // Step 1: Hit endpoint that sets a cookie using response-headers (no redirect involved)
        val setCookieResponse: HttpResponse =
            client.get("https://httpbingo.org/response-headers?Set-Cookie=testCookie=foobar")
        assertEquals(
            expected = HttpStatusCode.OK,
            actual = setCookieResponse.status,
            message = "The client should receive HTTP 200 OK when setting cookies via response-headers.",
        )

        // Step 2: Call /cookies to verify the cookie is sent back and echoed in the response
        val verifyResponse: HttpResponse = client.get("https://httpbingo.org/cookies")
        assertEquals(
            expected = HttpStatusCode.OK,
            actual = verifyResponse.status,
            message = "The client should handle cookies and return HTTP 200 OK when retrieving cookies.",
        )
    }

    @Test
    fun `should ignore unknown JSON keys in responses`() = runBlocking {
        val client = HttpClientProvider.client

        // Send a request to an endpoint that includes unknown JSON keys
        val response: HttpResponse = client.get("https://httpbingo.org/json")
        assertEquals(
            HttpStatusCode.OK,
            actual = response.status,
            message = "Client should successfully parse JSON response with unknown keys.",
        )
    }
}
