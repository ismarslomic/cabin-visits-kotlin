package no.slomic.smarthytte.vehicletrips.places

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Answer of the Kartverket place name search (`stedsnavn/v1/punkt`). Only the fields we use. */
@Serializable
data class KartverketPlaceNamesResponse(val navn: List<KartverketPlaceNameResponse> = emptyList())

@Serializable
data class KartverketPlaceNameResponse(
    // Defaults, so one odd entry among 40 is skipped by the filters instead of failing the whole answer
    val meterFraPunkt: Double = Double.MAX_VALUE,
    val navneobjekttype: String = "",
    val stedsnavn: List<KartverketSpellingResponse> = emptyList(),
)

@Serializable
data class KartverketSpellingResponse(
    @SerialName("skrivemåte") val spelling: String = "",
    @SerialName("skrivemåtestatus") val status: String = "",
)

/** Answer of the Kartverket address search (`adresser/v1/punktsok`). Only the fields we use. */
@Serializable
data class KartverketAddressesResponse(val adresser: List<KartverketAddressResponse> = emptyList())

@Serializable
data class KartverketAddressResponse(
    val adressetekst: String,
    val postnummer: String? = null,
    val poststed: String? = null,
)
