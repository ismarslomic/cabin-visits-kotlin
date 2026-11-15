package no.slomic.smarthytte.vehicletrips.jlr

import kotlinx.serialization.Serializable
import kotlin.time.Instant

/** One page of `GET /v1/vehicles/{vin}/journeys`. */
@Serializable
data class JlrJourneyListResponse(val items: List<JlrJourneyResponse>, val page: JlrPageResponse)

@Serializable
data class JlrPageResponse(val totalElements: Int, val totalPages: Int, val currentPage: Int, val pageSize: Int)

/** A journey from the JLR Data Hub. It has no energy values, address or city. */
@Serializable
data class JlrJourneyResponse(
    /** Numeric string, example: "8764829" */
    val journeyId: String,
    /** UTC */
    val startTime: Instant,
    /** UTC */
    val endTime: Instant,
    /** Seconds */
    val duration: Double,
    val distance: JlrValueUnitResponse,
    val averageSpeed: JlrValueUnitResponse,
    val startLocation: JlrLocationResponse,
    val endLocation: JlrLocationResponse,
)

@Serializable
data class JlrValueUnitResponse(val value: Double, val unit: String)

@Serializable
data class JlrLocationResponse(val latitude: Double, val longitude: Double)
