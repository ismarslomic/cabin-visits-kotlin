package no.slomic.smarthytte.calendar

interface CalendarEventRepository {
    suspend fun allEvents(): List<CalendarEvent>
    suspend fun eventById(id: String): CalendarEvent?
    suspend fun addOrUpdate(calendarEvent: CalendarEvent): CalendarEvent
    suspend fun deleteEvent(id: String): Boolean
    suspend fun syncToken(): String?
    suspend fun addOrUpdate(newSyncToken: String)
    suspend fun deleteSyncToken()
}
