package no.slomic.smarthytte.properties

data class GoogleCalendarPropertiesHolder(
    val googleCalendar: GoogleCalendarProperties,
)

data class GoogleCalendarProperties(
    val credentialsFilePath: String,
    val calendarId: String,
    val syncFromDateTime: String,
    val summaryToGuestFilePath: String,
)
