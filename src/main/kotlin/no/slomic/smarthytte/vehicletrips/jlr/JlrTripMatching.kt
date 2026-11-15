package no.slomic.smarthytte.vehicletrips.jlr

import no.slomic.smarthytte.vehicletrips.VehicleTrip
import kotlin.math.abs
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Rows of the old archive are found with `end_time` this close to the end of a journey. The archive has minute
 * precision, the journey exact seconds.
 */
val MATCH_CANDIDATE_WINDOW = 2.minutes

/** The id of an archive row is the end time in epoch milliseconds plus 0.6 to 2.5 seconds. */
private val ID_TOLERANCE_MILLIS = 10.seconds.inWholeMilliseconds

/** The archive and the Data Hub measure the distance of the same trip to within this many km. */
private const val DISTANCE_TOLERANCE_KM = 0.1

/**
 * The archive row that is the same trip as this journey, or null. A row matches when it is not linked to a journey yet,
 * its id (a number, the end time in epoch milliseconds) is within 10 seconds of the end of the journey, and its
 * distance within 0.1 km. The nearest id wins.
 */
fun JlrJourneyResponse.findMatchIn(candidates: List<VehicleTrip>): VehicleTrip? {
    val endMillis = endTime.toEpochMilliseconds()
    return candidates
        .filter { it.jlrJourneyId == null }
        .mapNotNull { trip -> trip.id.toLongOrNull()?.let { id -> trip to abs(id - endMillis) } }
        .filter { (trip, idDifference) ->
            idDifference <= ID_TOLERANCE_MILLIS && abs(trip.distance - distance.value) <= DISTANCE_TOLERANCE_KM
        }
        .minByOrNull { (_, idDifference) -> idDifference }
        ?.first
}
