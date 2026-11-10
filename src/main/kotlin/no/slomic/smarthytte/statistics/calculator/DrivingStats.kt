@file:Suppress("MaxLineLength")

package no.slomic.smarthytte.statistics.calculator

import io.ktor.openapi.JsonSchema
import kotlinx.serialization.Serializable

@Serializable
data class YearDrivingMomentStats(
    @JsonSchema.Description("The calendar year these driving moment statistics cover.")
    @JsonSchema.Example("2026")
    val year: Int,
    @JsonSchema.Description(
        "Average departure time from home (Oslo timezone) across all toCabin trips this year, expressed as minutes since midnight. E.g. 540 = 09:00.",
    )
    @JsonSchema.Example("930")
    val avgDepartureHomeMinutes: Int?,
    @JsonSchema.Description(
        "Average departure time from home formatted as HH:mm (Oslo timezone). Null if no trip data exists.",
    )
    @JsonSchema.Example("\"15:30\"")
    val avgDepartureHome: String?,
    @JsonSchema.Description(
        "Average arrival time at the cabin (Oslo timezone) across all toCabin trips this year, expressed as minutes since midnight.",
    )
    @JsonSchema.Example("1095")
    val avgArrivalCabinMinutes: Int?,
    @JsonSchema.Description(
        "Average arrival time at the cabin formatted as HH:mm (Oslo timezone). Null if no trip data exists.",
    )
    @JsonSchema.Example("\"18:15\"")
    val avgArrivalCabin: String?,
    @JsonSchema.Description(
        "Average departure time from the cabin (Oslo timezone) across all fromCabin trips this year, expressed as minutes since midnight.",
    )
    @JsonSchema.Example("840")
    val avgDepartureCabinMinutes: Int?,
    @JsonSchema.Description(
        "Average departure time from the cabin formatted as HH:mm (Oslo timezone). Null if no trip data exists.",
    )
    @JsonSchema.Example("\"14:00\"")
    val avgDepartureCabin: String?,
    @JsonSchema.Description(
        "Average arrival time back home (Oslo timezone) across all fromCabin trips this year, expressed as minutes since midnight.",
    )
    @JsonSchema.Example("1005")
    val avgArrivalHomeMinutes: Int?,
    @JsonSchema.Description(
        "Average arrival time back home formatted as HH:mm (Oslo timezone). Null if no trip data exists.",
    )
    @JsonSchema.Example("\"16:45\"")
    val avgArrivalHome: String?,
)

@Serializable
data class MonthDrivingMomentStats(
    @JsonSchema.Description("Month number (1 = January, 12 = December).")
    @JsonSchema.Example("7")
    val monthNumber: Int,
    @JsonSchema.Description("Localised month name (e.g. \"Januar\").")
    @JsonSchema.Example("\"Juli\"")
    val monthName: String,
    @JsonSchema.Description("The calendar year this month belongs to.")
    @JsonSchema.Example("2026")
    val year: Int,
    @JsonSchema.Description(
        "Average departure time from home (Oslo timezone) across all toCabin trips in this month, expressed as minutes since midnight.",
    )
    @JsonSchema.Example("930")
    val avgDepartureHomeMinutes: Int?,
    @JsonSchema.Description(
        "Average departure time from home formatted as HH:mm (Oslo timezone). Null if no trip data exists.",
    )
    @JsonSchema.Example("\"15:30\"")
    val avgDepartureHome: String?,
    @JsonSchema.Description(
        "Average arrival time at the cabin (Oslo timezone) across all toCabin trips in this month, expressed as minutes since midnight.",
    )
    @JsonSchema.Example("1095")
    val avgArrivalCabinMinutes: Int?,
    @JsonSchema.Description(
        "Average arrival time at the cabin formatted as HH:mm (Oslo timezone). Null if no trip data exists.",
    )
    @JsonSchema.Example("\"18:15\"")
    val avgArrivalCabin: String?,
    @JsonSchema.Description(
        "Average departure time from the cabin (Oslo timezone) across all fromCabin trips in this month, expressed as minutes since midnight.",
    )
    @JsonSchema.Example("840")
    val avgDepartureCabinMinutes: Int?,
    @JsonSchema.Description(
        "Average departure time from the cabin formatted as HH:mm (Oslo timezone). Null if no trip data exists.",
    )
    @JsonSchema.Example("\"14:00\"")
    val avgDepartureCabin: String?,
    @JsonSchema.Description(
        "Average arrival time back home (Oslo timezone) across all fromCabin trips in this month, expressed as minutes since midnight.",
    )
    @JsonSchema.Example("1005")
    val avgArrivalHomeMinutes: Int?,
    @JsonSchema.Description(
        "Average arrival time back home formatted as HH:mm (Oslo timezone). Null if no trip data exists.",
    )
    @JsonSchema.Example("\"16:45\"")
    val avgArrivalHome: String?,
)

@Serializable
data class YearDrivingTimeStats(
    @JsonSchema.Description("The calendar year these driving time statistics cover.")
    @JsonSchema.Example("2026")
    val year: Int,
    @JsonSchema.Description(
        "Average total driving duration to the cabin in minutes, summing all legs per trip (including stops). Null if no trip data exists.",
    )
    @JsonSchema.Example("165")
    val avgToCabinMinutes: Int?,
    @JsonSchema.Description("Average driving duration to the cabin formatted as HH:mm. Null if no trip data exists.")
    @JsonSchema.Example("\"02:45\"")
    val avgToCabin: String?,
    @JsonSchema.Description(
        "Shortest total driving duration to the cabin in minutes across all trips this year. Null if no trip data exists.",
    )
    @JsonSchema.Example("150")
    val minToCabinMinutes: Int?,
    @JsonSchema.Description("Shortest driving duration to the cabin formatted as HH:mm. Null if no trip data exists.")
    @JsonSchema.Example("\"02:30\"")
    val minToCabin: String?,
    @JsonSchema.Description(
        "Longest total driving duration to the cabin in minutes across all trips this year. Null if no trip data exists.",
    )
    @JsonSchema.Example("190")
    val maxToCabinMinutes: Int?,
    @JsonSchema.Description("Longest driving duration to the cabin formatted as HH:mm. Null if no trip data exists.")
    @JsonSchema.Example("\"03:10\"")
    val maxToCabin: String?,
    @JsonSchema.Description(
        "Average total driving duration from the cabin in minutes, summing all legs per trip (including stops). Null if no trip data exists.",
    )
    @JsonSchema.Example("160")
    val avgFromCabinMinutes: Int?,
    @JsonSchema.Description("Average driving duration from the cabin formatted as HH:mm. Null if no trip data exists.")
    @JsonSchema.Example("\"02:40\"")
    val avgFromCabin: String?,
    @JsonSchema.Description(
        "Shortest total driving duration from the cabin in minutes across all trips this year. Null if no trip data exists.",
    )
    @JsonSchema.Example("145")
    val minFromCabinMinutes: Int?,
    @JsonSchema.Description("Shortest driving duration from the cabin formatted as HH:mm. Null if no trip data exists.")
    @JsonSchema.Example("\"02:25\"")
    val minFromCabin: String?,
    @JsonSchema.Description(
        "Longest total driving duration from the cabin in minutes across all trips this year. Null if no trip data exists.",
    )
    @JsonSchema.Example("185")
    val maxFromCabinMinutes: Int?,
    @JsonSchema.Description("Longest driving duration from the cabin formatted as HH:mm. Null if no trip data exists.")
    @JsonSchema.Example("\"03:05\"")
    val maxFromCabin: String?,
)

@Serializable
data class MonthDrivingTimeStats(
    @JsonSchema.Description("Month number (1 = January, 12 = December).")
    @JsonSchema.Example("7")
    val monthNumber: Int,
    @JsonSchema.Description("Localised month name (e.g. \"Januar\").")
    @JsonSchema.Example("\"Juli\"")
    val monthName: String,
    @JsonSchema.Description("The calendar year this month belongs to.")
    @JsonSchema.Example("2026")
    val year: Int,
    @JsonSchema.Description(
        "Average total driving duration to the cabin in minutes this month, summing all legs per trip (including stops). Null if no trip data exists.",
    )
    @JsonSchema.Example("165")
    val avgToCabinMinutes: Int?,
    @JsonSchema.Description("Average driving duration to the cabin formatted as HH:mm. Null if no trip data exists.")
    @JsonSchema.Example("\"02:45\"")
    val avgToCabin: String?,
    @JsonSchema.Description(
        "Shortest total driving duration to the cabin in minutes across all trips this month. Null if no trip data exists.",
    )
    @JsonSchema.Example("150")
    val minToCabinMinutes: Int?,
    @JsonSchema.Description("Shortest driving duration to the cabin formatted as HH:mm. Null if no trip data exists.")
    @JsonSchema.Example("\"02:30\"")
    val minToCabin: String?,
    @JsonSchema.Description(
        "Longest total driving duration to the cabin in minutes across all trips this month. Null if no trip data exists.",
    )
    @JsonSchema.Example("190")
    val maxToCabinMinutes: Int?,
    @JsonSchema.Description("Longest driving duration to the cabin formatted as HH:mm. Null if no trip data exists.")
    @JsonSchema.Example("\"03:10\"")
    val maxToCabin: String?,
    @JsonSchema.Description(
        "Average total driving duration from the cabin in minutes this month, summing all legs per trip (including stops). Null if no trip data exists.",
    )
    @JsonSchema.Example("160")
    val avgFromCabinMinutes: Int?,
    @JsonSchema.Description("Average driving duration from the cabin formatted as HH:mm. Null if no trip data exists.")
    @JsonSchema.Example("\"02:40\"")
    val avgFromCabin: String?,
    @JsonSchema.Description(
        "Shortest total driving duration from the cabin in minutes across all trips this month. Null if no trip data exists.",
    )
    @JsonSchema.Example("145")
    val minFromCabinMinutes: Int?,
    @JsonSchema.Description("Shortest driving duration from the cabin formatted as HH:mm. Null if no trip data exists.")
    @JsonSchema.Example("\"02:25\"")
    val minFromCabin: String?,
    @JsonSchema.Description(
        "Longest total driving duration from the cabin in minutes across all trips this month. Null if no trip data exists.",
    )
    @JsonSchema.Example("185")
    val maxFromCabinMinutes: Int?,
    @JsonSchema.Description("Longest driving duration from the cabin formatted as HH:mm. Null if no trip data exists.")
    @JsonSchema.Example("\"03:05\"")
    val maxFromCabin: String?,
    @JsonSchema.Description(
        "Difference between avgToCabinMinutes this month and avgToCabinMinutes the previous calendar month. Positive means longer average drive than last month. Null if either month has no data.",
    )
    @JsonSchema.Example("5")
    val diffAvgToCabinMinutesVsPrevMonth: Int?,
    @JsonSchema.Description(
        "diffAvgToCabinMinutesVsPrevMonth formatted as +HH:mm or -HH:mm. Null if either month has no data.",
    )
    @JsonSchema.Example("\"+00:05\"")
    val diffAvgToCabinVsPrevMonth: String?,
    @JsonSchema.Description(
        "Difference between avgFromCabinMinutes this month and avgFromCabinMinutes the previous calendar month. Positive means longer average drive than last month. Null if either month has no data.",
    )
    @JsonSchema.Example("-3")
    val diffAvgFromCabinMinutesVsPrevMonth: Int?,
    @JsonSchema.Description(
        "diffAvgFromCabinMinutesVsPrevMonth formatted as +HH:mm or -HH:mm. Null if either month has no data.",
    )
    @JsonSchema.Example("\"-00:03\"")
    val diffAvgFromCabinVsPrevMonth: String?,
)
