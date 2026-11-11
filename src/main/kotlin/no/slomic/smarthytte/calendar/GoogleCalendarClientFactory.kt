package no.slomic.smarthytte.calendar

import com.google.api.client.http.HttpRequestInitializer
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.calendar.Calendar
import com.google.api.services.calendar.CalendarScopes
import com.google.auth.http.HttpCredentialsAdapter
import com.google.auth.oauth2.ServiceAccountCredentials
import no.slomic.smarthytte.properties.GoogleCalendarProperties
import java.io.FileInputStream
import java.io.IOException

private const val CONNECT_TIMEOUT_MILLIS = 10_000
private const val READ_TIMEOUT_MILLIS = 20_000

/**
 * Builds the Calendar client from the service account key file. `CALENDAR_EVENTS` is the narrowest scope that allows
 * insert, patch and delete; the calendar must be shared with the service account at "Make changes to events" level.
 * The timeouts apply to the calls to the Calendar API; the token fetch of the credentials uses its own transport.
 *
 * @throws IOException if the key file is missing or invalid
 */
fun createCalendarClient(properties: GoogleCalendarProperties): CalendarClient {
    val credentials = FileInputStream(properties.credentialsFilePath).use {
        ServiceAccountCredentials.fromStream(it).createScoped(listOf(CalendarScopes.CALENDAR_EVENTS))
    }
    val adapter = HttpCredentialsAdapter(credentials)
    val initializer = HttpRequestInitializer { request ->
        adapter.initialize(request)
        request.connectTimeout = CONNECT_TIMEOUT_MILLIS
        request.readTimeout = READ_TIMEOUT_MILLIS
    }
    val calendar = Calendar.Builder(NetHttpTransport(), GsonFactory.getDefaultInstance(), initializer)
        .setApplicationName("Cabin Visits Kotlin")
        .build()

    return CalendarClient(calendar, properties.calendarId)
}
