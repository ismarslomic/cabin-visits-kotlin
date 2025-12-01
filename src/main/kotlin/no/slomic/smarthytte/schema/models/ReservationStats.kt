package no.slomic.smarthytte.schema.models

import com.expediagroup.graphql.generator.annotations.GraphQLDescription

@GraphQLDescription("Statistics for reservations grouped by year with months as children")
data class YearReservationStats(
    @GraphQLDescription("The calendar year")
    val year: Int,

    @GraphQLDescription("Total number of visits (reservations) that start in this year")
    val totalVisits: Int,

    @GraphQLDescription(
        "Difference in visits compared to the previous 12 months (this year's total minus" +
            " previous 12 months); positive = increase, negative = decrease",
    )
    val comparedToLast12Months: Int,

    @GraphQLDescription("Average number of visits per month for this year (total/12)")
    val averageMonthlyVisits: Double,

    @GraphQLDescription(
        "Total number of occupied days (sum of stay days across all reservations) within this calendar year; " +
            "stays are counted as days from start (inclusive) to end (exclusive)",
    )
    val totalStayDays: Int,

    @GraphQLDescription(
        "Percentage of occupied days relative to the number of days in the year; " +
            "rounded to 1 decimal",
    )
    val percentDaysOccupied: Double,

    @GraphQLDescription(
        "Percentage of occupied weeks relative to the number of weeks that intersect this year; " +
            "a week is considered occupied if any day in that ISO week falls within a reservation; " +
            "rounded to 1 decimal",
    )
    val percentWeeksOccupied: Double,

    @GraphQLDescription(
        "Percentage of months with at least one occupied day, out of 12 months; " +
            "rounded to 1 decimal",
    )
    val percentMonthsOccupied: Double,

    @GraphQLDescription("Month with the most visits in this year")
    val monthMostVisits: MonthCount?,

    @GraphQLDescription("Month with the fewest visits in this year")
    val monthFewestVisits: MonthCount?,

    @GraphQLDescription("Month that had a reservation with the longest stay in this year")
    val monthWithLongestStay: MonthStay?,

    @GraphQLDescription("Monthly statistics for the year")
    val months: List<MonthReservationStats>,
)

@GraphQLDescription("Per-month statistics")
data class MonthReservationStats(
    @GraphQLDescription("Month number 1-12")
    val monthNumber: Int,

    @GraphQLDescription("Localized month name in English")
    val monthName: String,

    @GraphQLDescription("Total number of visits (reservations) that start in this month")
    val totalVisits: Int,

    @GraphQLDescription(
        "Difference in visits compared to the previous 30-day window before this month (this month " +
            "total minus previous 30 days); positive = increase, negative = decrease",
    )
    val comparedToLast30Days: Int,

    @GraphQLDescription(
        "Difference in visits compared to the same month last year (this month total minus last year same " +
            "month); positive = increase, negative = decrease",
    )
    val comparedToSameMonthLastYear: Int,

    @GraphQLDescription(
        "Difference from the year-to-date average up to and including this month (this month total minus YTD " +
            "average); positive = above average, negative = below average",
    )
    val comparedToYearToDateAverage: Double,

    @GraphQLDescription("Minimum stay length in days for reservations in this month; null if no reservations")
    val minStayDays: Int?,

    @GraphQLDescription("Maximum stay length in days for reservations in this month; null if no reservations")
    val maxStayDays: Int?,

    @GraphQLDescription("Average stay length in days for reservations in this month; null if no reservations")
    val avgStayDays: Double?,

    @GraphQLDescription(
        "Percentage of occupied days relative to the number of days in " +
            "the month; rounded to 1 decimal",
    )
    val percentDaysOccupied: Double,

    @GraphQLDescription(
        "Percentage of occupied ISO weeks relative to the number of ISO weeks that intersect " +
            "the month; a week is considered occupied if any day in that ISO week falls within a reservation; " +
            "rounded to 1 decimal",
    )
    val percentWeeksOccupied: Double,
)

data class MonthCount(val monthNumber: Int, val monthName: String, val count: Int)

data class MonthStay(val monthNumber: Int, val monthName: String, val days: Int)
