package no.slomic.smarthytte.calendarevents

import io.ktor.util.logging.KtorSimpleLogger
import io.ktor.util.logging.Logger
import kotlinx.datetime.Clock
import no.slomic.smarthytte.common.suspendTransaction
import no.slomic.smarthytte.common.truncatedToMillis

class SqliteGoogleCalendarRepository : GoogleCalendarRepository {
    private val logger: Logger = KtorSimpleLogger(SqliteGoogleCalendarRepository::class.java.name)
    private val synckTokenId: Int = 1

    override suspend fun syncToken(): String? =
        suspendTransaction { GoogleCalendarSyncEntity.findById(synckTokenId)?.syncToken }

    override suspend fun addOrUpdateSyncToken(newSyncToken: String) {
        suspendTransaction {
            val storedGoogleCalendarSync: GoogleCalendarSyncEntity? = GoogleCalendarSyncEntity.findById(synckTokenId)

            var isUpdated = false
            if (storedGoogleCalendarSync == null) {
                GoogleCalendarSyncEntity.new(synckTokenId) {
                    syncToken = newSyncToken
                    updatedTime = Clock.System.now().truncatedToMillis()
                    isUpdated = true
                }
            } else {
                storedGoogleCalendarSync.syncToken = newSyncToken

                val isDirty: Boolean = storedGoogleCalendarSync.writeValues.isNotEmpty()

                if (isDirty) {
                    storedGoogleCalendarSync.updatedTime = Clock.System.now().truncatedToMillis()
                    isUpdated = true
                }
            }

            if (isUpdated) {
                logger.trace("Calendar sync token updated.")
            } else {
                logger.trace("No need to update the calendar sync token.")
            }
        }
    }

    override suspend fun deleteSyncToken() {
        suspendTransaction {
            logger.trace("Deleting calendar sync token")
            val storedCalendarSync: GoogleCalendarSyncEntity? = GoogleCalendarSyncEntity.findById(synckTokenId)
            storedCalendarSync?.delete()
            logger.trace("Calendar sync token deleted")
        }
    }
}
