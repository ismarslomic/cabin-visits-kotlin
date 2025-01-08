package no.slomic.smarthytte.properties

import com.sksamuel.hoplite.ConfigLoaderBuilder
import com.sksamuel.hoplite.ExperimentalHoplite
import com.sksamuel.hoplite.addResourceSource
import com.sksamuel.hoplite.env.Environment

fun currentEnvironment(): Environment {
    val developmentFlag = System.getProperty("io.ktor.development")
    if (developmentFlag == "true") {
        return Environment.development
    }
    return Environment.fromEnvVar("KTOR_ENV", Environment.development)
}

@OptIn(ExperimentalHoplite::class)
inline fun <reified T : Any> loadProperties(): T {
    val environment = currentEnvironment()

    val properties: T = ConfigLoaderBuilder
        .default()
        .withExplicitSealedTypes()
        .addResourceSource(resource = "/application.yml")
        .addResourceSource(resource = "/application-${environment.name}.yml")
        .build()
        .loadConfigOrThrow<T>()

    return properties
}
