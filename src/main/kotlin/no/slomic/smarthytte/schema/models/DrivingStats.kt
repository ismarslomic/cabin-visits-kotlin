package no.slomic.smarthytte.schema.models

import com.expediagroup.graphql.generator.annotations.GraphQLDescription

@GraphQLDescription("Average departure/arrival times per year across cabin trips (times of day)")
data class DrivingMomentStatsYear(
    @GraphQLDescription("Calendar year for these stats")
    val year: Int,

    @GraphQLDescription("Average departure time-of-day from home, in minutes since midnight (Oslo time)")
    val avgDepartureHomeMinutes: Int?,
    @GraphQLDescription("Average departure time-of-day from home, formatted as HH:MM (Oslo time)")
    val avgDepartureHome: String?,

    @GraphQLDescription("Average arrival time-of-day at cabin, in minutes since midnight (Oslo time)")
    val avgArrivalCabinMinutes: Int?,
    @GraphQLDescription("Average arrival time-of-day at cabin, formatted as HH:MM (Oslo time)")
    val avgArrivalCabin: String?,

    @GraphQLDescription("Average departure time-of-day from cabin, in minutes since midnight (Oslo time)")
    val avgDepartureCabinMinutes: Int?,
    @GraphQLDescription("Average departure time-of-day from cabin, formatted as HH:MM (Oslo time)")
    val avgDepartureCabin: String?,

    @GraphQLDescription("Average arrival time-of-day at home, in minutes since midnight (Oslo time)")
    val avgArrivalHomeMinutes: Int?,
    @GraphQLDescription("Average arrival time-of-day at home, formatted as HH:MM (Oslo time)")
    val avgArrivalHome: String?,
)

@GraphQLDescription("Average departure/arrival times per month across cabin trips (times of day)")
data class DrivingMomentStatsMonth(
    @GraphQLDescription("Month number 1-12")
    val monthNumber: Int,
    @GraphQLDescription("Month name in English")
    val monthName: String,
    @GraphQLDescription("Calendar year for this month")
    val year: Int,

    @GraphQLDescription("Average departure time-of-day from home, in minutes since midnight (Oslo time)")
    val avgDepartureHomeMinutes: Int?,
    @GraphQLDescription("Average departure time-of-day from home, formatted as HH:MM (Oslo time)")
    val avgDepartureHome: String?,

    @GraphQLDescription("Average arrival time-of-day at cabin, in minutes since midnight (Oslo time)")
    val avgArrivalCabinMinutes: Int?,
    @GraphQLDescription("Average arrival time-of-day at cabin, formatted as HH:MM (Oslo time)")
    val avgArrivalCabin: String?,

    @GraphQLDescription("Average departure time-of-day from cabin, in minutes since midnight (Oslo time)")
    val avgDepartureCabinMinutes: Int?,
    @GraphQLDescription("Average departure time-of-day from cabin, formatted as HH:MM (Oslo time)")
    val avgDepartureCabin: String?,

    @GraphQLDescription("Average arrival time-of-day at home, in minutes since midnight (Oslo time)")
    val avgArrivalHomeMinutes: Int?,
    @GraphQLDescription("Average arrival time-of-day at home, formatted as HH:MM (Oslo time)")
    val avgArrivalHome: String?,
)

@GraphQLDescription("Driving time statistics for the current calendar year")
data class DrivingTimeStatsYear(
    @GraphQLDescription("Calendar year for these stats")
    val year: Int,

    @GraphQLDescription("Average driving time in minutes from home to cabin across all trips that arrived this year")
    val avgToCabinMinutes: Int?,
    @GraphQLDescription("Average driving time formatted as HH:MM from home to cabin")
    val avgToCabin: String?,
    @GraphQLDescription("Minimum driving time in minutes from home to cabin")
    val minToCabinMinutes: Int?,
    @GraphQLDescription("Minimum driving time formatted as HH:MM from home to cabin")
    val minToCabin: String?,
    @GraphQLDescription("Maximum driving time in minutes from home to cabin")
    val maxToCabinMinutes: Int?,
    @GraphQLDescription("Maximum driving time formatted as HH:MM from home to cabin")
    val maxToCabin: String?,

    @GraphQLDescription("Average driving time in minutes from cabin to home across all trips that departed this year")
    val avgFromCabinMinutes: Int?,
    @GraphQLDescription("Average driving time formatted as HH:MM from cabin to home")
    val avgFromCabin: String?,
    @GraphQLDescription("Minimum driving time in minutes from cabin to home")
    val minFromCabinMinutes: Int?,
    @GraphQLDescription("Minimum driving time formatted as HH:MM from cabin to home")
    val minFromCabin: String?,
    @GraphQLDescription("Maximum driving time in minutes from cabin to home")
    val maxFromCabinMinutes: Int?,
    @GraphQLDescription("Maximum driving time formatted as HH:MM from cabin to home")
    val maxFromCabin: String?,
)

@GraphQLDescription("Driving time statistics for the current calendar month")
data class DrivingTimeStatsMonth(
    @GraphQLDescription("Month number 1-12")
    val monthNumber: Int,
    @GraphQLDescription("Month name in English")
    val monthName: String,
    @GraphQLDescription("Calendar year for this month")
    val year: Int,

    @GraphQLDescription("Average driving time in minutes from home to cabin for trips that arrived this month")
    val avgToCabinMinutes: Int?,
    @GraphQLDescription("Average driving time formatted as HH:MM from home to cabin for this month")
    val avgToCabin: String?,
    @GraphQLDescription("Minimum driving time in minutes from home to cabin in this month")
    val minToCabinMinutes: Int?,
    @GraphQLDescription("Minimum driving time formatted as HH:MM from home to cabin in this month")
    val minToCabin: String?,
    @GraphQLDescription("Maximum driving time in minutes from home to cabin in this month")
    val maxToCabinMinutes: Int?,
    @GraphQLDescription("Maximum driving time formatted as HH:MM from home to cabin in this month")
    val maxToCabin: String?,

    @GraphQLDescription("Average driving time in minutes from cabin to home for trips that departed this month")
    val avgFromCabinMinutes: Int?,
    @GraphQLDescription("Average driving time formatted as HH:MM from cabin to home for this month")
    val avgFromCabin: String?,
    @GraphQLDescription("Minimum driving time in minutes from cabin to home in this month")
    val minFromCabinMinutes: Int?,
    @GraphQLDescription("Minimum driving time formatted as HH:MM from cabin to home in this month")
    val minFromCabin: String?,
    @GraphQLDescription("Maximum driving time in minutes from cabin to home in this month")
    val maxFromCabinMinutes: Int?,
    @GraphQLDescription("Maximum driving time formatted as HH:MM from cabin to home in this month")
    val maxFromCabin: String?,

    @GraphQLDescription(
        "Difference in average minutes from home to cabin vs previous month (this month avg - previous month avg)",
    )
    val diffAvgToCabinMinutesVsPrevMonth: Int?,
    @GraphQLDescription("Difference in average time formatted as ±HH:MM from home to cabin vs previous month")
    val diffAvgToCabinVsPrevMonth: String?,
    @GraphQLDescription(
        "Difference in average minutes from cabin to home vs previous month (this month avg - previous month avg)",
    )
    val diffAvgFromCabinMinutesVsPrevMonth: Int?,
    @GraphQLDescription("Difference in average time formatted as ±HH:MM from cabin to home vs previous month")
    val diffAvgFromCabinVsPrevMonth: String?,
)
