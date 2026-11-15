package no.slomic.smarthytte.vehicletrips.places

private const val VILLAGE_TYPE = "Bygdelag (bygd)"
private const val APPROVED_SPELLING = "godkjent og prioritert"

/** The approved name of the nearest village (`Bygdelag (bygd)`), the city we store for rural points. Null if none. */
fun KartverketPlaceNamesResponse.villageName(): String? = navn
    .filter { it.navneobjekttype == VILLAGE_TYPE }
    .sortedBy { it.meterFraPunkt }
    .firstNotNullOfOrNull { village -> village.stedsnavn.firstOrNull { it.status == APPROVED_SPELLING }?.spelling }

/** `Storgata 28B, 0184 Oslo`: street text, postal code and the postal town in title case. Null without an address. */
fun KartverketAddressesResponse.toAddress(): String? = adresser.firstOrNull()?.let { address ->
    val postalPlace = listOfNotNull(address.postnummer, address.poststed?.toTitleCase()).joinToString(" ")
    if (postalPlace.isEmpty()) address.adressetekst else "${address.adressetekst}, $postalPlace"
}

/** The postal town in title case (`OSLO` gives `Oslo`), the city when there is no village name. Null without one. */
fun KartverketAddressesResponse.postalTown(): String? = adresser.firstOrNull()?.poststed?.toTitleCase()

/** Small words in a Norwegian place name stay lowercase: `Mo i Rana`, `Våler i Solør`. */
private val SMALL_WORDS = setOf("i", "og")

private val letterRun = Regex("(?<!\\p{L})\\p{L}+")

/** Each run of letters gets a capital, space and hyphen stay as they are: `NORDRE-FOLLO` is `Nordre-Follo`. */
private fun String.toTitleCase(): String = lowercase().replace(letterRun) { word ->
    if (word.value in SMALL_WORDS) word.value else word.value.replaceFirstChar { it.uppercase() }
}
