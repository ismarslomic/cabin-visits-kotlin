package no.slomic.smarthytte.statistics.calculator

/**
 * Priority (0-100, higher is shown first) of every fun fact, grouped by tier. Facts with equal priority are sorted
 * in a stable order by the calculators.
 */
internal object FunFactPriority {
    // Event facts (80-100)
    const val FIRST_VISIT = 100
    const val VISIT_MILESTONE = 95
    const val CABIN_VISIT_MILESTONE = 85
    const val DAYS_MILESTONE = 85
    const val DRIVING_RECORD = 85
    const val LONG_ABSENCE = 80

    // Notable facts (50-79)
    const val ARRIVAL_SOON = 70
    const val TOP_GUEST_ALL_TIME = 70
    const val VISIT_STREAK = 70
    const val YEAR_TIED_LEAD = 70
    const val YEAR_LEADER = 65
    const val DRIVING_TIME_VS_LAST_YEAR = 60
    const val LAST_DAY = 60
    const val YEAR_GAP_TO_LEADER = 60
    const val FIRST_VISIT_THIS_YEAR = 55
    const val NEW_IN_GROUP = 55
    const val SHARE_OF_TRIPS_GUEST = 50

    // Everyday facts (20-49)
    const val COUNTDOWN_TO_NEXT_VISIT = 45
    const val NEXT_FIRST_TIMER = 45
    const val SHARE_OF_DAYS = 45
    const val SHARE_OF_LIFE = 45
    const val DAYS_THIS_YEAR = 40
    const val MOST_EXPERIENCED = 40
    const val NEW_GUESTS_THIS_YEAR = 40
    const val NEXT_VISIT_MILESTONE = 40
    const val YEARS_SINCE_FIRST_VISIT = 40
    const val MONTH_VS_LAST_YEAR = 35
    const val MOST_VISITED_MONTH = 35
    const val OCCUPANCY = 35
    const val AVG_GROUP_SIZE = 30
    const val LAST_VISIT_DATE = 30
    const val LONGEST_STAY = 30
    const val NIGHTS_LEFT = 30
    const val SHARE_OF_TRIPS_FAMILY = 30
    const val STAY_PROGRESS = 30
    const val TOTAL_DAYS = 30
    const val TOTAL_DISTANCE = 30
    const val CABIN_TOTALS = 25
    const val AVG_DRIVING_TIME = 20
    const val EV_CONSUMPTION = 20

    // Filler facts (0-19)
    const val YEARS_OF_OWNERSHIP = 15

    /** Subtracted from family facts when the reservation also has non-family guests. */
    const val FAMILY_PENALTY = 30
}
