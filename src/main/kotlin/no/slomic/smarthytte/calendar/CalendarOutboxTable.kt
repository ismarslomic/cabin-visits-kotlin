package no.slomic.smarthytte.calendar

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.datetime.timestamp
import kotlin.time.Instant

/** What the Google Calendar event of a reservation needs: created, moved (times changed) or removed. */
enum class CalendarOperation { INSERT, UPDATE, DELETE }

/**
 * The outbox of calendar notifications: one pending row per reservation change that changes the event. Internal, so no
 * audit columns. `reservationId` is the Google event id and has no foreign key, because a [CalendarOperation.DELETE]
 * row outlives the reservation.
 */
object CalendarOutboxTable : IntIdTable(name = "calendar_outbox") {
    val reservationId: Column<String> = varchar("reservation_id", length = 1024)
    val operation: Column<CalendarOperation> = enumerationByName("operation", length = 10, CalendarOperation::class)
    val createdTime: Column<Instant> = timestamp("created_time")
    val attempts: Column<Int> = integer("attempts").default(0)
    val lastError: Column<String?> = text("last_error").nullable()
    val nextAttemptTime: Column<Instant> = timestamp("next_attempt_time")
}
