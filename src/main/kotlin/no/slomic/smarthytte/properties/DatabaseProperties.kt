package no.slomic.smarthytte.properties

data class DatabasePropertiesHolder(
    val database: DatabaseProperties,
)

data class DatabaseProperties(
    val filePath: String,
)
