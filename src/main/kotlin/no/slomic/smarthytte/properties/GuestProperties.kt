package no.slomic.smarthytte.properties

data class GuestPropertiesHolder(
    val guest: GuestProperties,
)

data class GuestProperties(
    val filePath: String,
)
