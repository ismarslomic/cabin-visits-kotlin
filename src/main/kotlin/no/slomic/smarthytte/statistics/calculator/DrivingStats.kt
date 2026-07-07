@file:Suppress("MaxLineLength")

package no.slomic.smarthytte.statistics.calculator

import io.ktor.openapi.JsonSchema
import kotlinx.serialization.Serializable

@Serializable
data class DrivingMomentStatsYear(
    @JsonSchema.Description("The calendar year these driving moment statistics cover.")
    val year: Int,
    @JsonSchema.Description(
        "Average departure time from home (Oslo timezone) across all toCabin trips this year, expressed as minutes since midnight. E.g. 540 = 09:00.",
    )
    val avgDepartureHomeMinutes: Int?,
    @JsonSchema.Description(
        "Average departure time from home formatted as HH:mm (Oslo timezone). Null if no trip data exists.",
    )
    val avgDepartureHome: String?,
    @JsonSchema.Description(
        "Average arrival time at the cabin (Oslo timezone) across all toCabin trips this year, expressed as minutes since midnight.",
    )
    val avgArrivalCabinMinutes: Int?,
    @JsonSchema.Description(
        "Average arrival time at the cabin formatted as HH:mm (Oslo timezone). Null if no trip data exists.",
    )
    val avgArrivalCabin: String?,
    @JsonSchema.Description(
        "Average departure time from the cabin (Oslo timezone) across all fromCabin trips this year, expressed as minutes since midnight.",
    )
    val avgDepartureCabinMinutes: Int?,
    @JsonSchema.Description(
        "Average departure time from the cabin formatted as HH:mm (Oslo timezone). Null if no trip data exists.",
    )
    val avgDepartureCabin: String?,
    @JsonSchema.Description(
        "Average arrival time back home (Oslo timezone) across all fromCabin trips this year, expressed as minutes since midnight.",
    )
    val avgArrivalHomeMinutes: Int?,
    @JsonSchema.Description(
        "Average arrival time back home formatted as HH:mm (Oslo timezone). Null if no trip data exists.",
    )
    val avgArrivalHome: String?,
)

@Serializable
data class DrivingMomentStatsMonth(
    @JsonSchema.Description("Month number (1 = January, 12 = December).")
    val monthNumber: Int,
    @JsonSchema.Description("Localised month name (e.g. \"Januar\").")
    val monthName: String,
    @JsonSchema.Description("The calendar year this month belongs to.")
    val year: Int,
    @JsonSchema.Description(
        "Average departure time from home (Oslo timezone) across all toCabin trips in this month, expressed as minutes since midnight.",
    )
    val avgDepartureHomeMinutes: Int?,
    @JsonSchema.Description(
        "Average departure time from home formatted as HH:mm (Oslo timezone). Null if no trip data exists.",
    )
    val avgDepartureHome: String?,
    @JsonSchema.Description(
        "Average arrival time at the cabin (Oslo timezone) across all toCabin trips in this month, expressed as minutes since midnight.",
    )
    val avgArrivalCabinMinutes: Int?,
    @JsonSchema.Description(
        "Average arrival time at the cabin formatted as HH:mm (Oslo timezone). Null if no trip data exists.",
    )
    val avgArrivalCabin: String?,
    @JsonSchema.Description(
        "Average departure time from the cabin (Oslo timezone) across all fromCabin trips in this month, expressed as minutes since midnight.",
    )
    val avgDepartureCabinMinutes: Int?,
    @JsonSchema.Description(
        "Average departure time from the cabin formatted as HH:mm (Oslo timezone). Null if no trip data exists.",
    )
    val avgDepartureCabin: String?,
    @JsonSchema.Description(
        "Average arrival time back home (Oslo timezone) across all fromCabin trips in this month, expressed as minutes since midnight.",
    )
    val avgArrivalHomeMinutes: Int?,
    @JsonSchema.Description(
        "Average arrival time back home formatted as HH:mm (Oslo timezone). Null if no trip data exists.",
    )
    val avgArrivalHome: String?,
)

@Serializable
data class DrivingTimeStatsYear(
    @JsonSchema.Description("The calendar year these driving time statistics cover.")
    val year: Int,
    @JsonSchema.Description(
        "Average total driving duration to the cabin in minutes, summing all legs per trip (including stops). Null if no trip data exists.",
    )
    val avgToCabinMinutes: Int?,
    @JsonSchema.Description("Average driving duration to the cabin formatted as HH:mm. Null if no trip data exists.")
    val avgToCabin: String?,
    @JsonSchema.Description(
        "Shortest total driving duration to the cabin in minutes across all trips this year. Null if no trip data exists.",
    )
    val minToCabinMinutes: Int?,
    @JsonSchema.Description("Shortest driving duration to the cabin formatted as HH:mm. Null if no trip data exists.")
    val minToCabin: String?,
    @JsonSchema.Description(
        "Longest total driving duration to the cabin in minutes across all trips this year. Null if no trip data exists.",
    )
    val maxToCabinMinutes: Int?,
    @JsonSchema.Description("Longest driving duration to the cabin formatted as HH:mm. Null if no trip data exists.")
    val maxToCabin: String?,
    @JsonSchema.Description(
        "Average total driving duration from the cabin in minutes, summing all legs per trip (including stops). Null if no trip data exists.",
    )
    val avgFromCabinMinutes: Int?,
    @JsonSchema.Description("Average driving duration from the cabin formatted as HH:mm. Null if no trip data exists.")
    val avgFromCabin: String?,
    @JsonSchema.Description(
        "Shortest total driving duration from the cabin in minutes across all trips this year. Null if no trip data exists.",
    )
    val minFromCabinMinutes: Int?,
    @JsonSchema.Description("Shortest driving duration from the cabin formatted as HH:mm. Null if no trip data exists.")
    val minFromCabin: String?,
    @JsonSchema.Description(
        "Longest total driving duration from the cabin in minutes across all trips this year. Null if no trip data exists.",
    )
    val maxFromCabinMinutes: Int?,
    @JsonSchema.Description("Longest driving duration from the cabin formatted as HH:mm. Null if no trip data exists.")
    val maxFromCabin: String?,
)

@Serializable
data class DrivingTimeStatsMonth(
    @JsonSchema.Description("Month number (1 = January, 12 = December).")
    val monthNumber: Int,
    @JsonSchema.Description("Localised month name (e.g. \"Januar\").")
    val monthName: String,
    @JsonSchema.Description("The calendar year this month belongs to.")
    val year: Int,
    @JsonSchema.Description(
        "Average total driving duration to the cabin in minutes this month, summing all legs per trip (including stops). Null if no trip data exists.",
    )
    val avgToCabinMinutes: Int?,
    @JsonSchema.Description("Average driving duration to the cabin formatted as HH:mm. Null if no trip data exists.")
    val avgToCabin: String?,
    @JsonSchema.Description(
        "Shortest total driving duration to the cabin in minutes across all trips this month. Null if no trip data exists.",
    )
    val minToCabinMinutes: Int?,
    @JsonSchema.Description("Shortest driving duration to the cabin formatted as HH:mm. Null if no trip data exists.")
    val minToCabin: String?,
    @JsonSchema.Description(
        "Longest total driving duration to the cabin in minutes across all trips this month. Null if no trip data exists.",
    )
    val maxToCabinMinutes: Int?,
    @JsonSchema.Description("Longest driving duration to the cabin formatted as HH:mm. Null if no trip data exists.")
    val maxToCabin: String?,
    @JsonSchema.Description(
        "Average total driving duration from the cabin in minutes this month, summing all legs per trip (including stops). Null if no trip data exists.",
    )
    val avgFromCabinMinutes: Int?,
    @JsonSchema.Description("Average driving duration from the cabin formatted as HH:mm. Null if no trip data exists.")
    val avgFromCabin: String?,
    @JsonSchema.Description(
        "Shortest total driving duration from the cabin in minutes across all trips this month. Null if no trip data exists.",
    )
    val minFromCabinMinutes: Int?,
    @JsonSchema.Description("Shortest driving duration from the cabin formatted as HH:mm. Null if no trip data exists.")
    val minFromCabin: String?,
    @JsonSchema.Description(
        "Longest total driving duration from the cabin in minutes across all trips this month. Null if no trip data exists.",
    )
    val maxFromCabinMinutes: Int?,
    @JsonSchema.Description("Longest driving duration from the cabin formatted as HH:mm. Null if no trip data exists.")
    val maxFromCabin: String?,
    @JsonSchema.Description(
        "Difference between avgToCabinMinutes this month and avgToCabinMinutes the previous calendar month. Positive means longer average drive than last month. Null if either month has no data.",
    )
    val diffAvgToCabinMinutesVsPrevMonth: Int?,
    @JsonSchema.Description(
        "diffAvgToCabinMinutesVsPrevMonth formatted as +HH:mm or -HH:mm. Null if either month has no data.",
    )
    val diffAvgToCabinVsPrevMonth: String?,
    @JsonSchema.Description(
        "Difference between avgFromCabinMinutes this month and avgFromCabinMinutes the previous calendar month. Positive means longer average drive than last month. Null if either month has no data.",
    )
    val diffAvgFromCabinMinutesVsPrevMonth: Int?,
    @JsonSchema.Description(
        "diffAvgFromCabinMinutesVsPrevMonth formatted as +HH:mm or -HH:mm. Null if either month has no data.",
    )
    val diffAvgFromCabinVsPrevMonth: String?,
)
