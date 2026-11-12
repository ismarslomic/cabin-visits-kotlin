package no.slomic.smarthytte.reservations

import io.ktor.openapi.JsonSchema
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import no.slomic.smarthytte.checkinouts.CheckInOutSource
import no.slomic.smarthytte.guests.Guest
import no.slomic.smarthytte.guests.guestAvatarUrl
import kotlin.time.Instant

/**
 * A reservation as the API returns it, the same for every role. [startTime] and [endTime] are instants: statistics and
 * guest stay dates use their UTC date, so an Oslo time between 00:00 and 02:00 belongs to the previous UTC day.
 */
@Serializable
data class ReservationResponse(
    @JsonSchema.Description(
        "Unique identifier of the reservation. It is also the id of the Google Calendar event, so an old " +
            "reservation can have an id that starts with an underscore.",
    )
    @JsonSchema.Example("\"a3f9c2e17b4d4e5f8a1b2c3d4e5f6a7b\"")
    val id: String,
    @JsonSchema.Description("When the stay starts (UTC instant). Its UTC date is the first day of the stay.")
    @JsonSchema.Example("\"2026-07-10T16:00:00Z\"")
    val startTime: Instant,
    @JsonSchema.Description("When the stay ends (UTC instant). Its UTC date is the last day of the stay.")
    @JsonSchema.Example("\"2026-07-12T13:00:00Z\"")
    val endTime: Instant,
    @JsonSchema.Description("The guests of the reservation, sorted by guest id. Never empty for a stored reservation.")
    val guests: List<ReservationGuestResponse>,
    @JsonSchema.Description(
        "UPCOMING before the start date, ONGOING while the end date is after today, COMPLETED from the end date " +
            "itself on (UTC dates). Differs from the live stats, which keep the check-out day as occupied.",
    )
    @JsonSchema.Example("\"UPCOMING\"")
    val status: VisitStatus,
    @JsonSchema.Description("The check-in, or null if the reservation has no check-in yet.")
    val checkIn: ReservationCheckInResponse?,
    @JsonSchema.Description("The check-out, or null if the reservation has no check-out yet.")
    val checkOut: ReservationCheckOutResponse?,
    @JsonSchema.Description(
        "The version of the reservation. It changes only when a user edits the times or the guests, not when " +
            "check-in/out or trips are derived.",
    )
    @JsonSchema.Example("3")
    val version: Short,
    @JsonSchema.Description("When the reservation was created (UTC).")
    @JsonSchema.Example("\"2026-06-01T09:15:00Z\"")
    val createdTime: Instant,
    @JsonSchema.Description("Who created the reservation: a guest id, or the literal system, which is not a guest.")
    @JsonSchema.Example("\"ola.nordmann\"")
    val createdBy: String,
    @JsonSchema.Description("When a user last edited the reservation (UTC), or null if never edited.")
    @JsonSchema.Example("\"2026-06-20T18:30:00Z\"")
    val updatedTime: Instant?,
    @JsonSchema.Description(
        "Who last edited the reservation: a guest id, or the literal system, which is not a guest. Null if never " +
            "edited.",
    )
    @JsonSchema.Example("\"kari.nordmann\"")
    val updatedBy: String?,
)

/** A guest of a reservation. Names and avatar are read from the guest and cannot be changed through the reservation. */
@Serializable
data class ReservationGuestResponse(
    @JsonSchema.Description("Internal unique identifier of the guest.")
    @JsonSchema.Example("\"ola.nordmann\"")
    val guestId: String,
    @JsonSchema.Description("The first name of the guest.")
    @JsonSchema.Example("\"Ola\"")
    val firstName: String,
    @JsonSchema.Description("The last name of the guest.")
    @JsonSchema.Example("\"Nordmann\"")
    val lastName: String,
    @JsonSchema.Description(
        "Relative URL of the avatar image, or null if the guest has no avatar. The v parameter changes when the " +
            "image does, so the URL can be cached safely.",
    )
    @JsonSchema.Example("\"/api/guests/ola.nordmann/avatar?v=1767225600000\"")
    val avatarUrl: String?,
    @JsonSchema.Description(
        "The guest's own first day (UTC date) when the guest arrived after the reservation started, otherwise " +
            "null: the guest follows the start of the reservation. Compare with the UTC date of startTime.",
    )
    @JsonSchema.Example("\"2026-07-11\"")
    val fromDate: LocalDate?,
    @JsonSchema.Description(
        "The guest's own last day (UTC date) when the guest left before the reservation ended, otherwise null: " +
            "the guest follows the end of the reservation. Compare with the UTC date of endTime.",
    )
    @JsonSchema.Example("\"2026-07-12\"")
    val toDate: LocalDate?,
)

@Serializable
data class ReservationCheckInResponse(
    @JsonSchema.Description("When the guests checked in (UTC).")
    @JsonSchema.Example("\"2026-07-10T17:42:00Z\"")
    val time: Instant,
    @JsonSchema.Description("What registered the check-in: CHECK_IN_SENSOR, VEHICLE_TRIP or CALENDAR_EVENT.")
    @JsonSchema.Example("\"CHECK_IN_SENSOR\"")
    val sourceName: CheckInOutSource,
)

@Serializable
data class ReservationCheckOutResponse(
    @JsonSchema.Description("When the guests checked out (UTC).")
    @JsonSchema.Example("\"2026-07-12T12:10:00Z\"")
    val time: Instant,
    @JsonSchema.Description("What registered the check-out: CHECK_IN_SENSOR, VEHICLE_TRIP or CALENDAR_EVENT.")
    @JsonSchema.Example("\"VEHICLE_TRIP\"")
    val sourceName: CheckInOutSource,
)

/**
 * Maps a [Reservation] to its response. [guestsById] and [avatarVersionTimes] are loaded once per request; a guest
 * id that is not in [guestsById] cannot happen (the link is deleted with the guest) and is left out.
 */
fun Reservation.toResponse(
    guestsById: Map<String, Guest>,
    avatarVersionTimes: Map<String, Instant>,
    today: LocalDate,
): ReservationResponse = ReservationResponse(
    id = id,
    startTime = startTime,
    endTime = endTime,
    guests = guests.mapNotNull { stay -> guestsById[stay.guestId]?.let { it to stay } }.map { (guest, stay) ->
        ReservationGuestResponse(
            guestId = guest.id,
            firstName = guest.firstName,
            lastName = guest.lastName,
            avatarUrl = avatarVersionTimes[guest.id]?.let { time -> guestAvatarUrl(guest.id, time) },
            fromDate = stay.fromDate,
            toDate = stay.toDate,
        )
    },
    status = visitStatus(today),
    checkIn = checkIn?.let { ReservationCheckInResponse(it.time, it.sourceName) },
    checkOut = checkOut?.let { ReservationCheckOutResponse(it.time, it.sourceName) },
    version = version,
    createdTime = createdTime,
    createdBy = createdBy,
    updatedTime = updatedTime,
    updatedBy = updatedBy,
)
