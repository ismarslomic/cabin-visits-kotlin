package no.slomic.smarthytte.statistics.model

import kotlinx.serialization.Serializable

@Serializable
data class CurrentYearStats(val year: Int, val visits: Int, val stayDays: Int, val totalDistanceKm: Double?)
