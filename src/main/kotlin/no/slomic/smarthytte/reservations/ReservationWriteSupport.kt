package no.slomic.smarthytte.reservations

import io.ktor.util.logging.KtorSimpleLogger
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import no.slomic.smarthytte.common.ApiError
import no.slomic.smarthytte.common.toUtcDate
import no.slomic.smarthytte.common.truncatedToMillis
import org.sqlite.SQLiteErrorCode
import org.sqlite.SQLiteException
import java.sql.SQLException
import kotlin.time.Instant

internal const val MAX_NIGHTS = 60
internal const val MAX_GUESTS = 50
internal const val MAX_YEARS_AHEAD = 2

/** The result of creating, changing or deleting a reservation. */
sealed interface ReservationWriteResult {
    data class Created(val reservation: ReservationResponse) : ReservationWriteResult

    data class Updated(val reservation: ReservationResponse) : ReservationWriteResult

    data object Deleted : ReservationWriteResult

    data object NotFound : ReservationWriteResult

    /** The request breaks a rule: [code] is the [ApiError] code. */
    data class Invalid(val code: String, val message: String) : ReservationWriteResult

    /** The times overlap another reservation. */
    data object Conflict : ReservationWriteResult

    /** The client did not read the current version of the reservation. */
    data object VersionMismatch : ReservationWriteResult

    /** The database is busy or the calendar queue is full: try again later. */
    data class Unavailable(val message: String) : ReservationWriteResult
}

private val logger = KtorSimpleLogger("no.slomic.smarthytte.reservations.ReservationWriteService")

/** Validates the request and rejects the first rule it breaks, or returns null. [today] is the UTC date of now. */
internal fun invalidReservation(
    startTime: Instant,
    endTime: Instant,
    guestIds: List<String>,
    today: LocalDate,
    checkTimes: Boolean,
): ReservationWriteResult.Invalid? =
    invalidGuests(guestIds) ?: if (checkTimes) invalidTimes(startTime, endTime, today) else null

private fun invalidGuests(guestIds: List<String>): ReservationWriteResult.Invalid? = when {
    guestIds.isEmpty() -> invalid(ApiError.NO_GUESTS, "A reservation needs at least one guest")
    guestIds.size > MAX_GUESTS -> invalid(ApiError.TOO_MANY_GUESTS, "A reservation has at most $MAX_GUESTS guests")
    guestIds.toSet().size != guestIds.size -> invalid(ApiError.DUPLICATE_GUEST, "A guest is listed more than once")
    else -> null
}

private fun invalidTimes(startTime: Instant, endTime: Instant, today: LocalDate): ReservationWriteResult.Invalid? {
    val startDate = startTime.toUtcDate()

    return when {
        endTime <= startTime -> invalid(ApiError.END_BEFORE_START, "endTime must be after startTime")

        startDate.daysUntil(endTime.toUtcDate()) > MAX_NIGHTS ->
            invalid(ApiError.TOO_LONG, "A reservation is at most $MAX_NIGHTS nights")

        startDate > today.plus(MAX_YEARS_AHEAD, DateTimeUnit.YEAR) ->
            invalid(ApiError.TOO_FAR_AHEAD, "The start is at most $MAX_YEARS_AHEAD years ahead")

        else -> null
    }
}

private fun invalid(code: String, message: String) = ReservationWriteResult.Invalid(code, message)

/** The repository stores milliseconds, so only a difference in milliseconds is a change of the times. */
internal fun Reservation.hasTimes(startTime: Instant, endTime: Instant) =
    this.startTime == startTime.truncatedToMillis() && this.endTime == endTime.truncatedToMillis()

internal fun unknownGuests(ids: List<String>) =
    invalid(ApiError.UNKNOWN_GUEST, "These guests do not exist: ${ids.joinToString()}")

/** True if SQLite gave up waiting for the write lock (`SQLITE_BUSY` or `SQLITE_LOCKED`, also their extended codes). */
internal fun SQLException.isBusy(): Boolean = generateSequence<Throwable>(this) { it.cause }
    .filterIsInstance<SQLiteException>()
    .any { (it.resultCode.code and BASE_RESULT_CODE_MASK) in busyCodes }

private const val BASE_RESULT_CODE_MASK = 0xFF
private val busyCodes = setOf(SQLiteErrorCode.SQLITE_BUSY.code, SQLiteErrorCode.SQLITE_LOCKED.code)

/**
 * Runs a repository write. An exhausted busy timeout is [ReservationWriteResult.Unavailable] (503) instead of a 500;
 * any other database error is not handled here. Catches [SQLException], not only Exposed's wrapper, because
 * `commit()` and the lazy `BEGIN` throw the raw exception.
 */
internal suspend fun guardedWrite(block: suspend () -> ReservationWriteResult): ReservationWriteResult = try {
    block()
} catch (e: SQLException) {
    if (!e.isBusy()) throw e
    logger.warn("The reservation write gave up waiting for the database lock: ${e.message}")
    ReservationWriteResult.Unavailable("The database is busy, try again")
}
