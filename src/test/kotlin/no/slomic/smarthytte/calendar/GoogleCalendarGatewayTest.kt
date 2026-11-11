package no.slomic.smarthytte.calendar

import com.google.api.client.http.LowLevelHttpRequest
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.testing.http.MockHttpTransport
import com.google.api.client.testing.http.MockLowLevelHttpRequest
import com.google.api.client.testing.http.MockLowLevelHttpResponse
import com.google.api.services.calendar.Calendar
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.engine.spec.tempfile
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.types.shouldBeInstanceOf
import no.slomic.smarthytte.properties.GoogleCalendarProperties
import java.io.IOException
import java.util.Base64
import kotlin.time.Instant

private val times = CalendarEventTimes(Instant.parse("2026-07-10T16:00:00Z"), Instant.parse("2026-07-12T13:00:00Z"))
private const val EVENT_ID = "a3f9c2e17b4d4e5f8a1b2c3d4e5f6a7b"

private fun json(status: Int, body: String) = MockLowLevelHttpResponse()
    .setStatusCode(status)
    .setContentType("application/json")
    .setContent(body)

/** The mock request does not keep the url and the method of the call, so a transport records them. */
private class RecordingRequest(response: MockLowLevelHttpResponse) {
    val request: MockLowLevelHttpRequest = MockLowLevelHttpRequest().setResponse(response)
    var url: String = ""
    var method: String = ""
    val contentAsString: String get() = request.contentAsString.orEmpty()
}

private fun client(recording: RecordingRequest) = CalendarClient(
    Calendar.Builder(
        object : MockHttpTransport() {
            override fun buildRequest(method: String, url: String): LowLevelHttpRequest {
                recording.method = method
                recording.url = url
                return recording.request
            }
        },
        GsonFactory.getDefaultInstance(),
        null,
    ).setApplicationName("test").build(),
    "calendar-id",
)

/** A service account key file with a freshly generated key; nothing is sent to Google until a call is made. */
private fun serviceAccountKeyJson(): String {
    val privateKey = java.security.KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }
        .generateKeyPair().private
    val pem = Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(privateKey.encoded)

    return """
        {
          "type": "service_account",
          "project_id": "test",
          "private_key_id": "key-id",
          "private_key": "-----BEGIN PRIVATE KEY-----\n${pem.replace("\n", "\\n")}\n-----END PRIVATE KEY-----\n",
          "client_email": "test@test.iam.gserviceaccount.com",
          "client_id": "1",
          "token_uri": "https://oauth2.googleapis.com/token"
        }
    """.trimIndent()
}

class GoogleCalendarGatewayTest :
    StringSpec({
        fun request(status: Int = 200, body: String = "{}") = RecordingRequest(json(status, body))

        "insert should send a timed event with the reservation id, the title and no notification" {
            val request = request()
            val gateway = GoogleCalendarGateway({ client(request) }, "Hyttebesøk")

            gateway.insert(EVENT_ID, times) shouldBe CalendarCallResult.Success

            request.url shouldContain "/calendars/calendar-id/events"
            request.url shouldContain "sendUpdates=none"
            request.contentAsString shouldContain "\"id\":\"$EVENT_ID\""
            request.contentAsString shouldContain "\"summary\":\"Hyttebesøk\""
            request.contentAsString shouldContain "\"timeZone\":\"Europe/Oslo\""
            request.contentAsString shouldContain "\"dateTime\":\"2026-07-10T16:00:00.000Z\""
        }

        "patch should only send start and end, with date null so an all-day event becomes a timed one" {
            val request = request()
            val gateway = GoogleCalendarGateway({ client(request) }, "Hyttebesøk")

            gateway.patch(EVENT_ID, times, restore = false) shouldBe CalendarCallResult.Success

            request.method shouldBe "PATCH"
            request.url shouldContain "/events/$EVENT_ID"
            request.url shouldContain "sendUpdates=none"
            request.contentAsString shouldContain "\"date\":null"
            request.contentAsString shouldContain "\"dateTime\":\"2026-07-12T13:00:00.000Z\""
            request.contentAsString shouldNotContain "summary"
            request.contentAsString shouldNotContain "status"
        }

        "a restoring patch should also confirm the event" {
            val request = request()
            val gateway = GoogleCalendarGateway({ client(request) }, "Hyttebesøk")

            gateway.patch(EVENT_ID, times, restore = true) shouldBe CalendarCallResult.Success

            request.contentAsString shouldContain "\"status\":\"confirmed\""
        }

        "delete should call the event with no notification" {
            val request = request(204, "")
            val gateway = GoogleCalendarGateway({ client(request) }, "Hyttebesøk")

            gateway.delete(EVENT_ID) shouldBe CalendarCallResult.Success

            request.method shouldBe "DELETE"
            request.url shouldContain "/events/$EVENT_ID"
            request.url shouldContain "sendUpdates=none"
        }

        "an error answer should give the status and the Google reason" {
            val body = """{"error":{"code":403,"message":"slow down","errors":""" +
                """[{"domain":"usageLimits","reason":"rateLimitExceeded","message":"slow down"}]}}"""
            val gateway = GoogleCalendarGateway({ client(request(403, body)) }, "Hyttebesøk")

            val result = gateway.insert(EVENT_ID, times).shouldBeInstanceOf<CalendarCallResult.Failure>()

            result.status shouldBe 403
            result.reason shouldBe "rateLimitExceeded"
            result.action() shouldBe FailureAction.RETRY
        }

        "an error answer that is not JSON gives the status and no reason" {
            val request = RecordingRequest(MockLowLevelHttpResponse().setStatusCode(502))
            val gateway = GoogleCalendarGateway({ client(request) }, "Hyttebesøk")

            val result = gateway.delete(EVENT_ID).shouldBeInstanceOf<CalendarCallResult.Failure>()

            result.status shouldBe 502
            result.reason shouldBe null
        }

        "a network failure should give a failure without status" {
            val transport = object : MockHttpTransport() {
                override fun buildRequest(method: String, url: String): LowLevelHttpRequest =
                    throw IOException("network down")
            }
            val calendar = Calendar.Builder(transport, GsonFactory.getDefaultInstance(), null).build()
            val gateway = GoogleCalendarGateway({ CalendarClient(calendar, "calendar-id") }, "Hyttebesøk")

            val result = gateway.insert(EVENT_ID, times).shouldBeInstanceOf<CalendarCallResult.Failure>()

            result.status shouldBe null
            result.message shouldContain "network down"
        }

        "a client that cannot be created should fail the call and be created again by the next call" {
            @Suppress("CanBeVal") // written from the lambda below
            var created = 0
            val gateway = GoogleCalendarGateway(
                {
                    created++
                    if (created == 1) throw IOException("no credentials file") else client(request())
                },
                "Hyttebesøk",
            )

            gateway.insert(EVENT_ID, times).shouldBeInstanceOf<CalendarCallResult.Failure>().status shouldBe null
            gateway.insert(EVENT_ID, times) shouldBe CalendarCallResult.Success
            gateway.delete(EVENT_ID)

            // Created once more after the failure, then kept
            created shouldBe 2
        }

        "createCalendarClient should build the client for the calendar from a service account key file" {
            val keyFile = tempfile("service-account", ".json")
            keyFile.writeText(serviceAccountKeyJson())

            val client = createCalendarClient(GoogleCalendarProperties(keyFile.path, "calendar-id"))

            client.calendarId shouldBe "calendar-id"
            client.calendar.applicationName shouldBe "Cabin Visits Kotlin"
        }

        "createCalendarClient should fail with an IOException when the key file is missing" {
            shouldThrow<IOException> {
                createCalendarClient(GoogleCalendarProperties("/does/not/exist/key.json", "calendar-id"))
            }
        }
    })
