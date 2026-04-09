package no.slomic.smarthytte.skistats

import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
data class StatisticsPeriodResponse(val userId: String, val seasons: List<StatisticsSeason>, val updatedAtUtc: Instant)

@Serializable
data class StatisticsSeason(val id: String, val name: String, val type: String, val weeks: List<StatisticsWeek>)

@Serializable
data class StatisticsWeek(val id: String, val year: Int, val weekNumber: Int, val days: List<StatisticsDay>)

@Serializable
data class StatisticsDay(val date: String, val destinationIds: List<String>)
