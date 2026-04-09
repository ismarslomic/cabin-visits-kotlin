package no.slomic.smarthytte.skistats

import kotlinx.datetime.LocalDate
import kotlin.time.Instant

enum class PeriodType { DAY, WEEK, SEASON }

data class SkiLeaderboardPeriod(
    val type: PeriodType,
    val value: String,
    val startDate: LocalDate,
    val weekId: String?,
    val seasonId: String,
    val seasonName: String,
    val year: Int?,
    val weekNumber: Int?,
)

data class SkiProfile(val id: String, val name: String, val profileImageUrl: String?, val isPrivate: Boolean)

data class SkiLeaderboardEntry(
    val id: String,
    val profileId: String,
    val period: SkiLeaderboardPeriod,
    val leaderboardUpdatedAtUtc: Instant,
    val entryUserId: String,
    val position: Int,
    val dropHeightInMeter: Int,
)
