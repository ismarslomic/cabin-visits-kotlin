@file:Suppress("MaxLineLength")

package no.slomic.smarthytte.statistics.model

import io.ktor.openapi.JsonSchema
import kotlinx.serialization.Serializable
import no.slomic.smarthytte.statistics.calculator.YearDrivingMomentStats
import no.slomic.smarthytte.statistics.calculator.YearDrivingTimeStats

@Serializable
data class YearStats(
    @JsonSchema.Description("The calendar year these statistics cover.")
    @JsonSchema.Example("2026")
    val year: Int,
    @JsonSchema.Description("Visit count for this year with comparisons against the previous year.")
    val visits: YearVisitsStats,
    @JsonSchema.Description("Occupied-day statistics for this year (including departure day).")
    val days: YearDaysStats,
    @JsonSchema.Description("Occupied-night statistics for this year (excluding departure day).")
    val nights: YearNightsStats,
    val occupancy: OccupancyStats,
    val topGuestByDays: TopGuestByDays?,
    @JsonSchema.Description(
        "Guests who arrive in this year (guest's own arrival date) and had no stay that arrived in the previous year. Sorted by total days descending.",
    )
    val newGuests: List<GuestVisitStats>,
    @JsonSchema.Description(
        "All guests present in this year, sorted by total days descending. Basis: the guest's own stay (falls back to the reservation period when the guest has no own dates). Visits count stays that arrive in this year (guest's own arrival date), days count the guest's own days within this year.",
    )
    val guests: List<GuestVisitStats>,
    @JsonSchema.Description(
        "Driving distance statistics for trips to, from, and at the cabin this year. Null if no vehicle trip data exists.",
    )
    val drivingDistance: YearDrivingDistanceStats?,
    @JsonSchema.Description(
        "Electric vehicle energy statistics for trips to and from the cabin this year. Null if no vehicle trip data exists.",
    )
    val ev: YearEvStats?,
    @JsonSchema.Description(
        "Driving duration statistics (avg/min/max) for trips to and from the cabin this year. Null if no vehicle trip data exists.",
    )
    val drivingTime: YearDrivingTimeStats?,
    @JsonSchema.Description(
        "Average departure and arrival clock times for trips to and from the cabin this year, expressed in Oslo timezone. Null if no vehicle trip data exists.",
    )
    val drivingMoments: YearDrivingMomentStats?,
    @JsonSchema.Description("Statistics broken down for each of the 12 calendar months in this year.")
    val months: List<MonthStats>,
)

@Serializable
data class YearVisitsStats(
    @JsonSchema.Description("Number of reservations that started in this calendar year.")
    @JsonSchema.Example("5")
    val totalVisits: Int,
    @JsonSchema.Description(
        "Difference between this year's visit count and the previous year's visit count. Positive means more visits than last year.",
    )
    @JsonSchema.Example("2")
    val comparedToPreviousYear: Int,
    @JsonSchema.Description("Average number of visits per calendar month (totalVisits / 12).")
    @JsonSchema.Example("1.5")
    val avgMonthlyVisits: Double,
    @JsonSchema.Description(
        "Average number of guests per reservation this year (reservation period: every linked guest counts, whatever their own stay). Null if there are no reservations.",
    )
    @JsonSchema.Example("4.2")
    val avgGroupSize: Double?,
    val monthMostVisits: MonthlyVisitCount?,
    val monthFewestVisits: MonthlyVisitCount?,
)

@Serializable
data class YearDaysStats(
    @JsonSchema.Description(
        "Total number of occupied days in this year across all reservations, counting overlapping reservations only once per day. Departure day is counted as an occupied day.",
    )
    @JsonSchema.Example("14")
    val totalDays: Int,
    @JsonSchema.Description(
        "Shortest stay in days (full duration, arrival and departure day included) among reservations that started in this year. Null if no reservations exist.",
    )
    @JsonSchema.Example("2")
    val minDays: Int?,
    @JsonSchema.Description(
        "Longest stay in days (full duration, arrival and departure day included) among reservations that started in this year. Null if no reservations exist.",
    )
    @JsonSchema.Example("6")
    val maxDays: Int?,
    @JsonSchema.Description(
        "Average stay in days (full duration, arrival and departure day included) among reservations that started in this year. Null if no reservations exist.",
    )
    @JsonSchema.Example("3.5")
    val avgDays: Double?,
    @JsonSchema.Description(
        "Difference between totalDays for this year and totalDays for the previous year. Positive means more occupied days than last year.",
    )
    @JsonSchema.Example("2")
    val comparedToPreviousYear: Int,
    @JsonSchema.Description(
        "The month in which the single longest individual stay (by days) started this year. Null if no reservations exist.",
    )
    val monthLongestVisit: MonthlyDaysCount?,
)

@Serializable
data class YearNightsStats(
    @JsonSchema.Description(
        "Total number of occupied nights in this year across all reservations, counting overlapping reservations only once per night. Departure night is not counted.",
    )
    @JsonSchema.Example("10")
    val totalNights: Int,
    @JsonSchema.Description(
        "Shortest stay in nights (full duration) among reservations that started in this year. Null if no reservations exist.",
    )
    @JsonSchema.Example("1")
    val minNights: Int?,
    @JsonSchema.Description(
        "Longest stay in nights (full duration) among reservations that started in this year. Null if no reservations exist.",
    )
    @JsonSchema.Example("5")
    val maxNights: Int?,
    @JsonSchema.Description(
        "Average stay in nights (full duration) among reservations that started in this year. Null if no reservations exist.",
    )
    @JsonSchema.Example("2.5")
    val avgNights: Double?,
    @JsonSchema.Description("Average number of occupied nights per calendar month (totalNights / 12).")
    @JsonSchema.Example("3.8")
    val avgMonthlyNights: Double,
    @JsonSchema.Description(
        "Difference between totalNights for this year and totalNights for the previous year. Positive means more occupied nights than last year.",
    )
    @JsonSchema.Example("2")
    val comparedToPreviousYear: Int,
)

@Serializable
data class YearDrivingDistanceStats(
    @JsonSchema.Description(
        "Total driving distance in kilometres for all trips to the cabin this year.",
    )
    @JsonSchema.Example("1240.5")
    val totalToCabinKm: Double,
    @JsonSchema.Description("Shortest single trip distance to the cabin in km this year. Null if no trips exist.")
    @JsonSchema.Example("148.2")
    val minToCabinKm: Double?,
    @JsonSchema.Description("Longest single trip distance to the cabin in km this year. Null if no trips exist.")
    @JsonSchema.Example("162.7")
    val maxToCabinKm: Double?,
    @JsonSchema.Description("Average trip distance to the cabin in km this year. Null if no trips exist.")
    @JsonSchema.Example("155.1")
    val avgToCabinKm: Double?,
    @JsonSchema.Description(
        "Total driving distance in kilometres for all trips from the cabin this year.",
    )
    @JsonSchema.Example("1235.8")
    val totalFromCabinKm: Double,
    @JsonSchema.Description("Shortest single trip distance from the cabin in km this year. Null if no trips exist.")
    @JsonSchema.Example("147.9")
    val minFromCabinKm: Double?,
    @JsonSchema.Description("Longest single trip distance from the cabin in km this year. Null if no trips exist.")
    @JsonSchema.Example("163.4")
    val maxFromCabinKm: Double?,
    @JsonSchema.Description("Average trip distance from the cabin in km this year. Null if no trips exist.")
    @JsonSchema.Example("154.5")
    val avgFromCabinKm: Double?,
    @JsonSchema.Description(
        "Total driving distance in kilometres for all trips at (around) the cabin this year.",
    )
    @JsonSchema.Example("86.4")
    val totalAtCabinKm: Double,
    @JsonSchema.Description(
        "Average of VehicleTrip.averageSpeed across all toCabin legs, for the fastest reservation trip to the cabin this year. Null if no trips exist.",
    )
    @JsonSchema.Example("72.5")
    val minAvgSpeedToCabinKmh: Double?,
    @JsonSchema.Description(
        "Average of VehicleTrip.averageSpeed across all toCabin legs, for the slowest reservation trip to the cabin this year. Null if no trips exist.",
    )
    @JsonSchema.Example("91.0")
    val maxAvgSpeedToCabinKmh: Double?,
    @JsonSchema.Description(
        "Mean of avgSpeedKmh across all reservations with a toCabin trip this year. Null if no trips exist.",
    )
    @JsonSchema.Example("83.2")
    val avgAvgSpeedToCabinKmh: Double?,
    @JsonSchema.Description(
        "Average of VehicleTrip.averageSpeed across all fromCabin legs, for the fastest reservation trip from the cabin this year. Null if no trips exist.",
    )
    @JsonSchema.Example("70.8")
    val minAvgSpeedFromCabinKmh: Double?,
    @JsonSchema.Description(
        "Average of VehicleTrip.averageSpeed across all fromCabin legs, for the slowest reservation trip from the cabin this year. Null if no trips exist.",
    )
    @JsonSchema.Example("89.5")
    val maxAvgSpeedFromCabinKmh: Double?,
    @JsonSchema.Description(
        "Mean of avgSpeedKmh across all reservations with a fromCabin trip this year. Null if no trips exist.",
    )
    @JsonSchema.Example("81.6")
    val avgAvgSpeedFromCabinKmh: Double?,
)

@Serializable
data class YearEvStats(
    @JsonSchema.Description(
        "Total energy consumed (kWh) for all trips to and from the cabin this year, derived from average consumption × distance per trip. Null if no vehicle trip data exists.",
    )
    @JsonSchema.Example("245.6")
    val totalEnergyConsumedKwh: Double?,
    @JsonSchema.Description(
        "Distance-weighted average energy consumption in kWh per 100 km across all trips to and from the cabin this year. Null if no vehicle trip data exists.",
    )
    @JsonSchema.Example("19.8")
    val avgEnergyConsumptionKwhPer100km: Double?,
    @JsonSchema.Description(
        "Total energy regenerated (kWh) through regenerative braking across all trips to and from the cabin this year. Null if no vehicle trip data exists.",
    )
    @JsonSchema.Example("38.2")
    val totalEnergyRegeneratedKwh: Double?,
)

@JsonSchema.Description("Number of reservation starts in a given month.")
@Serializable
data class MonthlyVisitCount(
    @JsonSchema.Description("Month number (1 = January, 12 = December).")
    @JsonSchema.Example("7")
    val monthNumber: Int,
    @JsonSchema.Description("Localised month name (e.g. \"Januar\").")
    @JsonSchema.Example("\"Juli\"")
    val monthName: String,
    @JsonSchema.Description("Number of reservation starts in this month.")
    @JsonSchema.Example("2")
    val visitCount: Int,
)

@Serializable
data class MonthlyDaysCount(
    @JsonSchema.Description("Month number (1 = January, 12 = December).")
    @JsonSchema.Example("7")
    val monthNumber: Int,
    @JsonSchema.Description("Localised month name (e.g. \"Januar\").")
    @JsonSchema.Example("\"Juli\"")
    val monthName: String,
    @JsonSchema.Description("Duration in days of the longest individual stay that started in this month.")
    @JsonSchema.Example("6")
    val daysCount: Int,
)

/**
 * The guest with the most total days at the cabin in the year, exposed as [YearStats.topGuestByDays].
 *
 * This is deliberately a field-for-field copy of [GuestVisitStats] and exists only to keep the generated OpenAPI
 * document clean.
 *
 * **Problem it solves:** `topGuestByDays` is nullable, while the guest lists ([YearStats.guests] and
 * [YearStats.newGuests]) use [GuestVisitStats] as a non-null element type. When Ktor's OpenAPI inference sees the
 * same class used both as nullable and as non-null, it emits two schema variants: `GuestVisitStats` (typed
 * `["object", "null"]`) and a suffixed duplicate `GuestVisitStats2`. Client code generators (for example
 * `openapi-typescript`) then produce confusing, unstable type names.
 *
 * **Trade-off:** the six fields are duplicated here and in [GuestVisitStats], so a new field must be added to both
 * (and to [toTopGuestByDays]). The JSON on the wire is identical to what a [GuestVisitStats] would produce, so
 * clients are not affected.
 *
 * **Alternatives considered:** make `topGuestByDays` non-null (changes the meaning when no guest data exists), or
 * remove it, since it always equals the first element of [YearStats.guests] (breaking API change).
 */
@JsonSchema.Description("The guest with the most total days at the cabin in the year.")
@Serializable
data class TopGuestByDays(
    @JsonSchema.Description("Internal unique identifier of the guest.")
    @JsonSchema.Example("\"ola.nordmann\"")
    val guestId: String,
    @JsonSchema.Description("Guest's first name.")
    @JsonSchema.Example("\"Ola\"")
    val firstName: String,
    @JsonSchema.Description("Guest's last name.")
    @JsonSchema.Example("\"Nordmann\"")
    val lastName: String,
    @JsonSchema.Description("Guest's age in the reference year (ageYear - birthYear).")
    @JsonSchema.Example("38")
    val age: Int,
    @JsonSchema.Description(
        "Number of stays of the guest that arrived in the year (guest's own arrival date; the reservation start when the guest has no own dates).",
    )
    @JsonSchema.Example("5")
    val totalVisits: Int,
    @JsonSchema.Description(
        "Total number of days of the guest's own stay within the year (falls back to the reservation period when the guest has no own dates).",
    )
    @JsonSchema.Example("14")
    val totalDays: Int,
)

fun GuestVisitStats.toTopGuestByDays() = TopGuestByDays(
    guestId = guestId,
    firstName = firstName,
    lastName = lastName,
    age = age,
    totalVisits = totalVisits,
    totalDays = totalDays,
)

@JsonSchema.Description(
    "A guest's visits and days at the cabin within a period (year or month), counted with the guest's own stay (falls back to the reservation period when the guest has no own dates).",
)
@Serializable
data class GuestVisitStats(
    @JsonSchema.Description("Internal unique identifier of the guest.")
    @JsonSchema.Example("\"ola.nordmann\"")
    val guestId: String,
    @JsonSchema.Description("Guest's first name.")
    @JsonSchema.Example("\"Ola\"")
    val firstName: String,
    @JsonSchema.Description("Guest's last name.")
    @JsonSchema.Example("\"Nordmann\"")
    val lastName: String,
    @JsonSchema.Description("Guest's age in the reference year (ageYear - birthYear).")
    @JsonSchema.Example("38")
    val age: Int,
    @JsonSchema.Description(
        "Number of stays of the guest that arrived in the period (guest's own arrival date; the reservation start when the guest has no own dates).",
    )
    @JsonSchema.Example("5")
    val totalVisits: Int,
    @JsonSchema.Description(
        "Total number of days of the guest's own stay within the period, counting only days that fall within the period boundary (falls back to the reservation period when the guest has no own dates).",
    )
    @JsonSchema.Example("14")
    val totalDays: Int,
) {
    companion object {
        val COMPARATOR: Comparator<GuestVisitStats> = compareByDescending<GuestVisitStats> { it.totalDays }
            .thenByDescending { it.totalVisits }
            .thenBy { it.lastName }
            .thenBy { it.firstName }
    }
}
