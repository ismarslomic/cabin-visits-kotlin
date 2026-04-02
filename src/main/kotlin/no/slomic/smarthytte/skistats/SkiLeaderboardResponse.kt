package no.slomic.smarthytte.skistats

import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

// Using non-nullable types on properties absolutely required in this app, all other properties are nice to have
@Serializable
data class FriendsLeaderboardResponse(
    val userId: String,
    val periodData: LeaderboardPeriodData,
    val entries: List<LeaderboardEntry>,
    val user: LeaderboardEntry,
    val updatedAtUtc: Instant,
)

@Serializable
data class LeaderboardPeriodData(
    val periodType: String,
    val startDate: LocalDate,
    val weekId: String? = null,
    val seasonId: String,
)

@Serializable
data class LeaderboardEntry(
    val position: Int,
    val userId: String,
    val isPrivate: Boolean,
    val name: String,
    @SerialName("value")
    val dropHeightInMeter: Int,
    val profileImageUrl: String? = null,
)

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
