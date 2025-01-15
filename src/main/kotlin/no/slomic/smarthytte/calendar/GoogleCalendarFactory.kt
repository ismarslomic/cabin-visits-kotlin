package no.slomic.smarthytte.calendar

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.util.DateTime
import com.google.api.services.calendar.Calendar
import com.google.api.services.calendar.CalendarScopes
import com.google.auth.http.HttpCredentialsAdapter
import com.google.auth.oauth2.GoogleCredentials
import com.google.auth.oauth2.ServiceAccountCredentials
import io.ktor.util.logging.KtorSimpleLogger
import io.ktor.util.logging.Logger
import no.slomic.smarthytte.properties.GoogleCalendarProperties
import no.slomic.smarthytte.properties.GoogleCalendarPropertiesHolder
import no.slomic.smarthytte.properties.loadProperties
import java.io.FileInputStream

fun createGoogleCalendarService(calendarRepository: CalendarEventRepository): GoogleCalendarService {
    val googleProperties: GoogleCalendarProperties = loadProperties<GoogleCalendarPropertiesHolder>().googleCalendar
    val calendarApiClient: Calendar = createCalendarApiClient()
    val googleCalendarLogger: Logger = KtorSimpleLogger(GoogleCalendarService::class.java.name)
    val calendarId: String = googleProperties.calendarId
    val syncFromDateTime = DateTime(googleProperties.syncFromDateTime)
    val summaryToGuestFilePath: String = googleProperties.summaryToGuestFilePath

    return GoogleCalendarService(
        calendarApiClient = calendarApiClient,
        calendarRepository = calendarRepository,
        logger = googleCalendarLogger,
        calendarId = calendarId,
        syncFromDateTime = syncFromDateTime,
        summaryToGuestFilePath,
    )
}

private fun createCalendarApiClient(): Calendar {
    val googleProperties = loadProperties<GoogleCalendarPropertiesHolder>().googleCalendar
    val googleServiceAccountKeys = FileInputStream(googleProperties.credentialsFilePath)

    // Load service account credentials
    val credentials: GoogleCredentials =
        ServiceAccountCredentials.fromStream(googleServiceAccountKeys)
            .createScoped(listOf(CalendarScopes.CALENDAR_READONLY))

    val requestInitializer = HttpCredentialsAdapter(credentials)

    // Build the Calendar API client
    return Calendar.Builder(
        GoogleNetHttpTransport.newTrustedTransport(),
        GsonFactory.getDefaultInstance(),
        requestInitializer,
    ).setApplicationName("Cabin Visits Kotlin").build()
}
