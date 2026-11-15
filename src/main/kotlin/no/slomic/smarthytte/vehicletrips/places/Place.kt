package no.slomic.smarthytte.vehicletrips.places

/**
 * City and address of a point, each null when the lookup found none. The city decides check-in/out, the address is
 * display only.
 */
data class Place(val city: String?, val address: String?)
