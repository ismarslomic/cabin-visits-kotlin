@file:Suppress("TooManyFunctions")

package no.slomic.smarthytte.statistics.calculator

import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.yearsUntil
import no.slomic.smarthytte.common.groupThousands
import no.slomic.smarthytte.common.norwegianDuration
import no.slomic.smarthytte.common.norwegianMonthNameOf
import no.slomic.smarthytte.common.norwegianShortMonthYear
import no.slomic.smarthytte.common.round1
import no.slomic.smarthytte.common.toNorwegianDecimal
import no.slomic.smarthytte.statistics.model.FunFact
import no.slomic.smarthytte.statistics.model.YearStats
import kotlin.math.abs
import kotlin.math.roundToInt

// Cabin visit milestone: every this many visits
private const val CABIN_VISIT_MILESTONE_STEP = 50

// Driving time vs last year: smallest difference worth mentioning
private const val MIN_DRIVING_TIME_DIFF_MINUTES = 5

// Most visited month: fewest visits in the month to mention it
private const val MIN_MONTH_VISITS_FOR_MOST_VISITED = 2

// Total distance as laps around the earth: shown from this many laps
private const val EARTH_CIRCUMFERENCE_KM = 40_075.0
private const val MIN_LAPS_AROUND_EARTH = 0.1

// Years of ownership: the cabin was bought in December 2019
private val OWNERSHIP_START = LocalDate(2019, 12, 1)

/** All-time totals for the cabin; reservations that have not started are excluded. */
data class CabinTotals(val visits: Int, val nights: Int, val uniqueGuests: Int)

/** Everything the cabin fun facts depend on. The year stats are optional so a fact is skipped without its source. */
data class CabinFunFactContext(
    val today: LocalDate,
    val dataStartDate: LocalDate,
    val isOccupied: Boolean,
    val totals: CabinTotals,
    val currentYear: YearStats? = null,
    val previousYear: YearStats? = null,
)

/** Fun facts about the cabin as a whole, sorted by priority (highest first). */
fun calculateCabinFunFacts(context: CabinFunFactContext): List<FunFact> {
    val year = context.currentYear
    return (
        listOfNotNull(
            cabinVisitMilestone(context),
            cabinTotals(context),
            newGuestsThisYear(year),
            occupancyThisYear(year),
            mostVisitedMonth(year),
            monthVsSameMonthLastYear(context),
            longestStay(year),
            avgGroupSize(year),
            totalDistance(year),
            evConsumption(year),
            avgDrivingTime(year),
            yearsOfOwnership(context.today),
        ) + drivingTimeVsLastYear(context)
        ).sortedByDescending { it.priority }
}

private fun cabinVisitMilestone(context: CabinFunFactContext): FunFact? = funFact(
    null,
    FunFactPriority.CABIN_VISIT_MILESTONE,
) {
    text("Dette er hyttebesøk nr. ")
    stat(context.totals.visits)
    text(" siden ${context.dataStartDate.norwegianShortMonthYear()}")
}.takeIf { context.isOccupied && context.totals.visits > 0 && context.totals.visits % CABIN_VISIT_MILESTONE_STEP == 0 }

private fun cabinTotals(context: CabinFunFactContext): FunFact = funFact(null, FunFactPriority.CABIN_TOTALS) {
    text("Siden ${context.dataStartDate.norwegianShortMonthYear()}: ")
    stat("${context.totals.visits} besøk")
    text(", ")
    stat("${context.totals.nights} netter")
    text(" og ")
    stat("${context.totals.uniqueGuests} ulike gjester")
}

/** Average driving time compared with the same month last year, or with last year when the month has no data. */
private fun drivingTimeVsLastYear(context: CabinFunFactContext): List<FunFact> {
    val month = context.today.month
    val thisMonth = context.currentYear?.months?.getOrNull(month.ordinal)?.drivingTime
    val lastYearMonth = context.previousYear?.months?.getOrNull(month.ordinal)?.drivingTime
    val thisYear = context.currentYear?.drivingTime
    val lastYear = context.previousYear?.drivingTime
    val monthLabel = "i ${norwegianMonthNameOf(month)} i fjor"
    return listOfNotNull(
        drivingTimeFact(
            "Turen til hytta",
            MinutesComparison(thisMonth?.avgToCabinMinutes, lastYearMonth?.avgToCabinMinutes),
            MinutesComparison(thisYear?.avgToCabinMinutes, lastYear?.avgToCabinMinutes),
            monthLabel,
        ),
        drivingTimeFact(
            "Turen fra hytta",
            MinutesComparison(thisMonth?.avgFromCabinMinutes, lastYearMonth?.avgFromCabinMinutes),
            MinutesComparison(thisYear?.avgFromCabinMinutes, lastYear?.avgFromCabinMinutes),
            monthLabel,
        ),
    )
}

private data class MinutesComparison(val current: Int?, val previous: Int?) {
    val isComplete: Boolean get() = current != null && previous != null
    val diff: Int? get() = if (current != null && previous != null) current - previous else null
}

private fun drivingTimeFact(
    subject: String,
    month: MinutesComparison,
    year: MinutesComparison,
    monthLabel: String,
): FunFact? {
    val comparison = if (month.isComplete) month else year
    val label = if (month.isComplete) monthLabel else "i fjor"
    return comparison.diff
        ?.takeIf { abs(it) >= MIN_DRIVING_TIME_DIFF_MINUTES }
        ?.let {
            val direction = if (it < 0) "kortere" else "lengre"
            funFact(null, FunFactPriority.DRIVING_TIME_VS_LAST_YEAR) {
                text("$subject er ")
                stat("${abs(it)} min $direction")
                text(" enn $label")
            }
        }
}

private fun newGuestsThisYear(year: YearStats?): FunFact? = year?.newGuests?.size?.takeIf { it > 0 }?.let {
    funFact(null, FunFactPriority.NEW_GUESTS_THIS_YEAR) {
        stat(if (it == 1) "1 ny gjest" else "$it nye gjester")
        text(" på hytta i år")
    }
}

private fun occupancyThisYear(year: YearStats?): FunFact? =
    year?.occupancy?.dayOccupancy?.roundToInt()?.takeIf { it >= 1 }?.let {
        funFact(null, FunFactPriority.OCCUPANCY) {
            text("Hytta har vært i bruk ")
            stat("$it %")
            text(" av årets dager")
        }
    }

private fun mostVisitedMonth(year: YearStats?): FunFact? =
    year?.visits?.monthMostVisits?.takeIf { it.visitCount >= MIN_MONTH_VISITS_FOR_MOST_VISITED }?.let {
        val monthName = norwegianMonthNameOf(Month(it.monthNumber))
        funFact(null, FunFactPriority.MOST_VISITED_MONTH) {
            text("Mest besøkte måned i år: ")
            stat(monthName)
            text(" (${it.visitCount} besøk)")
        }
    }

private fun monthVsSameMonthLastYear(context: CabinFunFactContext): FunFact? {
    val month = context.today.month
    val diff = context.currentYear?.months?.getOrNull(month.ordinal)?.visits?.comparedToSameMonthLastYear
    return diff?.takeIf { it != 0 }?.let {
        val moreOrFewer = if (it > 0) "flere" else "færre"
        funFact(null, FunFactPriority.MONTH_VS_LAST_YEAR) {
            text("Så langt i ${norwegianMonthNameOf(month)}: ")
            stat("${abs(it)} $moreOrFewer besøk")
            text(" enn i fjor")
        }
    }
}

private fun longestStay(year: YearStats?): FunFact? = year?.nights?.maxNights?.takeIf { it >= 1 }?.let {
    funFact(null, FunFactPriority.LONGEST_STAY) {
        text("Årets lengste opphold: ")
        stat("$it netter")
    }
}

private fun avgGroupSize(year: YearStats?): FunFact? = year?.visits?.avgGroupSize?.let {
    funFact(null, FunFactPriority.AVG_GROUP_SIZE) {
        text("I snitt ")
        stat("${it.round1().toNorwegianDecimal()} personer")
        text(" per besøk i år")
    }
}

/** Total km driven to and from the cabin this year, as laps around the earth. */
private fun totalDistance(year: YearStats?): FunFact? {
    val distance = year?.drivingDistance ?: return null
    val km = distance.totalToCabinKm + distance.totalFromCabinKm
    val laps = (km / EARTH_CIRCUMFERENCE_KM).round1()
    return funFact(null, FunFactPriority.TOTAL_DISTANCE) {
        text("Årets hytteturer: ")
        stat("${km.roundToInt().groupThousands()} km")
        text(", ")
        stat("${laps.toNorwegianDecimal()} ganger")
        text(" rundt jorda")
    }.takeIf { laps >= MIN_LAPS_AROUND_EARTH }
}

private fun evConsumption(year: YearStats?): FunFact? = year?.ev?.avgEnergyConsumptionKwhPer100km?.let {
    funFact(null, FunFactPriority.EV_CONSUMPTION) {
        text("Strømforbruk på hyttetur i år: ")
        stat("${it.round1().toNorwegianDecimal()} kWh")
        text(" per 100 km")
    }
}

private fun avgDrivingTime(year: YearStats?): FunFact? {
    val driving = year?.drivingTime
    val toCabin = driving?.avgToCabinMinutes
    val fromCabin = driving?.avgFromCabinMinutes
    return if (toCabin != null && fromCabin != null) {
        funFact(null, FunFactPriority.AVG_DRIVING_TIME) {
            text("Snitt kjøretid i år: ")
            stat(norwegianDuration(toCabin))
            text(" til hytta og ")
            stat(norwegianDuration(fromCabin))
            text(" hjem")
        }
    } else {
        null
    }
}

private fun yearsOfOwnership(today: LocalDate): FunFact? = OWNERSHIP_START.yearsUntil(today).takeIf { it >= 1 }?.let {
    funFact(null, FunFactPriority.YEARS_OF_OWNERSHIP) {
        text("Hytta har vært i familien i ")
        stat("$it år")
    }
}
