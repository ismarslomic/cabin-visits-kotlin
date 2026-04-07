package no.slomic.smarthytte.skistats

import kotlinx.datetime.LocalDate
import kotlin.time.Instant

data class SkiProfile(val id: String, val name: String, val profileImageUrl: String?, val isPrivate: Boolean)

data class SkiLeaderboardEntry(
    val id: String,
    val profileId: String,
    val periodType: PeriodType,
    val periodValue: String,
    val startDate: LocalDate,
    val weekId: String?,
    val seasonId: String,
    val leaderboardUpdatedAtUtc: Instant,
    val entryUserId: String,
    val position: Int,
    val dropHeightInMeter: Int,
)
