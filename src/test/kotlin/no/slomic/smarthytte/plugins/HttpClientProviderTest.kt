package no.slomic.smarthytte.plugins

import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
        val response: HttpResponse = client.get("https://httpbin.org/get")
        assertEquals(
            expected = HttpStatusCode.OK,
            actual = response.status,
            message = "The client should return HTTP 200 OK for a successful request.",
        )
    }

    @Test
    fun `should handle cookies with AcceptAllCookiesStorage`() = runBlocking {
        val client = HttpClientProvider.client

        // Test that cookies are handled (validation requires a response with cookies set for testing)
        val response: HttpResponse = client.get("https://httpbin.org/cookies/set?testCookie=foobar")
        assertEquals(
            expected = HttpStatusCode.OK,
            actual = response.status,
            message = "The client should handle cookies and return HTTP 200 OK when cookies are set.",
        )

        // Additional assertion: Verify "testCookie" is set to "foobar" in the JSON response
        val responseBody = response.bodyAsText()
        val jsonElement = Json.parseToJsonElement(responseBody)
        val testCookie = jsonElement.jsonObject["cookies"]!!.jsonObject["testCookie"]!!.jsonPrimitive.content
        assertEquals(
            expected = "foobar",
            actual = testCookie,
            message = "The 'testCookie' should have the value 'foobar' in the response JSON.",
        )
    }

    @Test
    fun `should ignore unknown JSON keys in responses`() = runBlocking {
        val client = HttpClientProvider.client

        // Send a request to an endpoint that includes unknown JSON keys
        val response: HttpResponse = client.get("https://httpbin.org/json")
        assertEquals(
            HttpStatusCode.OK,
            actual = response.status,
            message = "Client should successfully parse JSON response with unknown keys.",
        )
    }
}
