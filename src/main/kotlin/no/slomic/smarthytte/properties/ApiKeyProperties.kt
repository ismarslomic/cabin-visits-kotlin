package no.slomic.smarthytte.properties

data class ApiKeyPropertiesHolder(val apiKey: ApiKeyProperties)

/**
 * API keys of the machines that may call the routes that allow services.
 *
 * @property keys Comma separated `name:sha256` entries, where sha256 is the hex encoded SHA-256 of the key. Only the
 * hash is configured, so the key itself is not in the configuration. Blank means no service can authenticate.
 */
data class ApiKeyProperties(val keys: String)
