package no.slomic.smarthytte.statistics.model

import kotlinx.serialization.Serializable

@Serializable
data class GuestStats(
    val topGuestsByVisits: List<GuestRanking>,
    val topGuestsByDays: List<GuestRanking>,
    val genderDistribution: GenderDistribution,
    val ageGroups: List<AgeGroup>,
)

@Serializable
data class GuestRanking(
    val guestId: String,
    val firstName: String,
    val lastName: String,
    val totalVisits: Int,
    val totalDays: Int,
)

@Serializable
data class GenderDistribution(
    val maleCount: Int,
    val femaleCount: Int,
    val malePercent: Double,
    val femalePercent: Double,
)

@Serializable
data class AgeGroup(val label: String, val guestCount: Int, val percent: Double)
