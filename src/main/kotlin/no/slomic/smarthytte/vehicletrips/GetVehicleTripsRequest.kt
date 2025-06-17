package no.slomic.smarthytte.vehicletrips

import kotlinx.datetime.LocalDate
import kotlinx.datetime.format
import kotlinx.datetime.format.Padding
import kotlinx.datetime.format.char
import kotlinx.serialization.Required
import kotlinx.serialization.Serializable

@Serializable
data class GetVehicleTripsRequest(
    /** Example: 01.02.2025 */
    val fromDateInUserFormat: String,
    /** Example: 11.02.2025 */
    val toDateInUserFormat: String,
    @Required
    val fetchJourneys: Boolean = true,
    @Required
    val currentPage: Int = 1,
    @Required
    val pageSize: Int = 20,
    @Required
    val sorting: List<String> = listOf("START_DATE-asc"),
    @Required
    val selectedJourneys: List<String> = emptyList(),
) {
    companion object {
        // Format: dd.mm.yyyy
        val NORWEGIAN_DATE_FORMAT = LocalDate.Format {
            dayOfMonth(padding = Padding.ZERO)
            char('.')
            monthNumber(padding = Padding.ZERO)
            char('.')
            year()
        }
    }
}

fun createRequest(fromDate: LocalDate, toDate: LocalDate, currentPage: Int = 1, pageSize: Int = 20) =
    GetVehicleTripsRequest(
        fromDateInUserFormat = fromDate.format(GetVehicleTripsRequest.NORWEGIAN_DATE_FORMAT),
        toDateInUserFormat = toDate.format(GetVehicleTripsRequest.NORWEGIAN_DATE_FORMAT),
        currentPage = currentPage,
        pageSize = pageSize,
    )
