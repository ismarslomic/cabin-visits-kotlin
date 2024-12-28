package no.slomic.smarthytte.properties

data class GooglePropertiesHolder(
    val google: GoogleProperties,
)

data class GoogleProperties(
    val credentialsFilePath: String,
    val tokensDirectoryPath: String,
)
