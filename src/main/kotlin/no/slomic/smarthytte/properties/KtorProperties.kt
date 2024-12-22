package no.slomic.smarthytte.properties

data class KtorPropertiesHolder(
    val ktor: KtorProperties,
)

data class KtorProperties(
    val deployment: DeploymentProperties,
)

data class DeploymentProperties(
    val host: String,
    val port: Int,
)
