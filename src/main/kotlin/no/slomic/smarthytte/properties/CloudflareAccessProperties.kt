package no.slomic.smarthytte.properties

data class CloudflareAccessPropertiesHolder(val cloudflareAccess: CloudflareAccessProperties)

/**
 * Cloudflare Access settings. Both are optional so that deployments without Cloudflare Access keep working: when either
 * is blank, endpoints that require authentication respond 503 instead of trusting any token.
 *
 * @property teamDomain Team domain of the Access organization, e.g. `myteam.cloudflareaccess.com`.
 * @property audience The `AUD` tag of the Access application that protects the API.
 */
data class CloudflareAccessProperties(val teamDomain: String, val audience: String) {
    val isConfigured: Boolean get() = teamDomain.isNotBlank() && audience.isNotBlank()
}
