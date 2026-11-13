@file:Suppress("MaxLineLength")

package no.slomic.smarthytte.statistics.model

import io.ktor.openapi.JsonSchema
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import no.slomic.smarthytte.reservations.GuestStay

/**
 * [GuestStats] of a guest of the current or next reservation, with the dates of the guest's own stay. The properties
 * are the same as in [GuestStats] (kept in sync by a test), because a client that reads the live statistics must not
 * break. The guest profile uses [GuestStats] without the stay dates: its numbers are not tied to one reservation.
 */
@JsonSchema.Description(
    "A guest of the current or next reservation: the statistics of the guest as of today, counted with the guest's " +
        "own stays (a guest's own dates, or the reservation period when the guest has no own dates) that have " +
        "started, and the dates of the guest's own stay in this reservation. The reservation lists every linked " +
        "guest, so a guest who has not arrived yet or has already left is listed too: compare stayStartDate and " +
        "stayEndDate with today.",
)
@Serializable
data class LiveGuestStats(
    @JsonSchema.Description("Internal unique identifier of the guest.")
    @JsonSchema.Example("\"ola.nordmann\"")
    val guestId: String,
    @JsonSchema.Description("Guest's first name.")
    @JsonSchema.Example("\"Ola\"")
    val firstName: String,
    @JsonSchema.Description("Guest's last name.")
    @JsonSchema.Example("\"Nordmann\"")
    val lastName: String,
    @JsonSchema.Description("Guest's age in the current year (current year - birthYear).")
    @JsonSchema.Example("38")
    val age: Int,
    @JsonSchema.Description("True if the guest is a family member (owner of the cabin), false for other guests.")
    @JsonSchema.Example("true")
    val isFamily: Boolean,
    @JsonSchema.Description(
        "Relative URL to the guest's avatar image (JPEG), see GET /api/guests/{guestId}/avatar. " +
            "Null if the guest has no avatar.",
    )
    @JsonSchema.Example("\"/api/guests/ola.nordmann/avatar?v=1767225600000\"")
    val avatarUrl: String?,
    @JsonSchema.Description(
        "True if the guest has no stay that started before this one (guest's own arrival date). In a guest " +
            "profile this is true only when the guest is at the cabin right now and the ongoing visit is the " +
            "first one.",
    )
    @JsonSchema.Example("false")
    val isFirstVisit: Boolean,
    @JsonSchema.Description(
        "Own arrival date of the guest's first started stay. Null if the guest has no started stays.",
    )
    @JsonSchema.Example("\"2019-02-16\"")
    val firstVisitDate: LocalDate?,
    @JsonSchema.Description(
        "Own departure date of the guest's last completed stay before this one. Null if there is no completed stay. " +
            "In a guest profile: the last visit before the current one if the guest is at the cabin right now, " +
            "otherwise the last completed visit.",
    )
    @JsonSchema.Example("\"2026-04-05\"")
    val lastVisitDate: LocalDate?,
    @JsonSchema.Description("Years in which the guest has arrived (own arrival date), in descending order.")
    @JsonSchema.Example("[2026, 2025, 2023]")
    val yearsVisited: List<Int>,
    val currentYear: GuestPeriodStats,
    val allTime: GuestPeriodStats,
    @JsonSchema.Description(
        "First day of the guest's own stay in this reservation (inclusive): the guest's own arrival date, or the " +
            "first day of the reservation when the guest has no own dates.",
    )
    @JsonSchema.Example("\"2026-07-11\"")
    val stayStartDate: LocalDate,
    @JsonSchema.Description(
        "Last day of the guest's own stay in this reservation: the guest's own departure date, or the last day of " +
            "the reservation when the guest has no own dates. Same semantics as the end date of the reservation.",
    )
    @JsonSchema.Example("\"2026-07-12\"")
    val stayEndDate: LocalDate,
)

fun GuestStats.toLive(stay: GuestStay): LiveGuestStats = LiveGuestStats(
    guestId = guestId,
    firstName = firstName,
    lastName = lastName,
    age = age,
    isFamily = isFamily,
    avatarUrl = avatarUrl,
    isFirstVisit = isFirstVisit,
    firstVisitDate = firstVisitDate,
    lastVisitDate = lastVisitDate,
    yearsVisited = yearsVisited,
    currentYear = currentYear,
    allTime = allTime,
    stayStartDate = stay.startDate,
    stayEndDate = stay.endDate,
)

/** The statistics without the stay dates, for the calculations (fun facts) that work on [GuestStats]. */
fun LiveGuestStats.toGuestStats(): GuestStats = GuestStats(
    guestId = guestId,
    firstName = firstName,
    lastName = lastName,
    age = age,
    isFamily = isFamily,
    avatarUrl = avatarUrl,
    isFirstVisit = isFirstVisit,
    firstVisitDate = firstVisitDate,
    lastVisitDate = lastVisitDate,
    yearsVisited = yearsVisited,
    currentYear = currentYear,
    allTime = allTime,
)
