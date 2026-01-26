package no.slomic.smarthytte.skistats

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
data class OAuthTokenResponse(
    @SerialName("refresh_token")
    val refreshToken: String,
    @SerialName("access_token")
    val accessToken: String,
    @SerialName("user_id")
    val userId: String? = null,
    @SerialName("client_id")
    val clientId: String? = null,
    val environment: String? = null,
    @SerialName("agent_id")
    val agentId: String? = null,
    val issuedAtUtc: Instant? = null,
    val expiresAtUtc: Instant? = null,
    @SerialName("token_type")
    val tokenType: String? = null,
    @SerialName("expires_in")
    val expiresIn: Long? = null,
    val scope: String? = null,
)
