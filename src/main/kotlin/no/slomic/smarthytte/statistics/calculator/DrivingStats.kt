package no.slomic.smarthytte.statistics.calculator

import kotlinx.serialization.Serializable

@Serializable
data class DrivingMomentStatsYear(
    val year: Int,
    val avgDepartureHomeMinutes: Int?,
    val avgDepartureHome: String?,
    val avgArrivalCabinMinutes: Int?,
    val avgArrivalCabin: String?,
    val avgDepartureCabinMinutes: Int?,
    val avgDepartureCabin: String?,
    val avgArrivalHomeMinutes: Int?,
    val avgArrivalHome: String?,
)

@Serializable
data class DrivingMomentStatsMonth(
    val monthNumber: Int,
    val monthName: String,
    val year: Int,
    val avgDepartureHomeMinutes: Int?,
    val avgDepartureHome: String?,
    val avgArrivalCabinMinutes: Int?,
    val avgArrivalCabin: String?,
    val avgDepartureCabinMinutes: Int?,
    val avgDepartureCabin: String?,
    val avgArrivalHomeMinutes: Int?,
    val avgArrivalHome: String?,
)

@Serializable
data class DrivingTimeStatsYear(
    val year: Int,
    val avgToCabinMinutes: Int?,
    val avgToCabin: String?,
    val minToCabinMinutes: Int?,
    val minToCabin: String?,
    val maxToCabinMinutes: Int?,
    val maxToCabin: String?,
    val avgFromCabinMinutes: Int?,
    val avgFromCabin: String?,
    val minFromCabinMinutes: Int?,
    val minFromCabin: String?,
    val maxFromCabinMinutes: Int?,
    val maxFromCabin: String?,
)

@Serializable
data class DrivingTimeStatsMonth(
    val monthNumber: Int,
    val monthName: String,
    val year: Int,
    val avgToCabinMinutes: Int?,
    val avgToCabin: String?,
    val minToCabinMinutes: Int?,
    val minToCabin: String?,
    val maxToCabinMinutes: Int?,
    val maxToCabin: String?,
    val avgFromCabinMinutes: Int?,
    val avgFromCabin: String?,
    val minFromCabinMinutes: Int?,
    val minFromCabin: String?,
    val maxFromCabinMinutes: Int?,
    val maxFromCabin: String?,
    val diffAvgToCabinMinutesVsPrevMonth: Int?,
    val diffAvgToCabinVsPrevMonth: String?,
    val diffAvgFromCabinMinutesVsPrevMonth: Int?,
    val diffAvgFromCabinVsPrevMonth: String?,
)
