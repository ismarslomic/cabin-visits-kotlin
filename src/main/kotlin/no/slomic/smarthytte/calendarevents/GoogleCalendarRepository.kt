package no.slomic.smarthytte.calendarevents

interface GoogleCalendarRepository {
    suspend fun syncToken(): String?
    suspend fun addOrUpdateSyncToken(newSyncToken: String)
    suspend fun deleteSyncToken()
}
