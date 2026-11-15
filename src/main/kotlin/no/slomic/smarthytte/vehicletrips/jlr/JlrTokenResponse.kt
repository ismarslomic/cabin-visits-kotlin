package no.slomic.smarthytte.vehicletrips.jlr

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Answer of the token endpoint. Both tokens are secrets. */
@Serializable
data class JlrTokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("expires_in") val expiresIn: Long,
    @SerialName("refresh_expires_in") val refreshExpiresIn: Long? = null,
) {
    // A data class would print the tokens in toString
    override fun toString(): String = "JlrTokenResponse(expiresIn=$expiresIn, refreshExpiresIn=$refreshExpiresIn)"
}

/** Answer of the device authorization endpoint: where the owner approves the login. */
@Serializable
data class JlrDeviceAuthorizationResponse(
    @SerialName("device_code") val deviceCode: String,
    @SerialName("user_code") val userCode: String,
    @SerialName("verification_uri") val verificationUri: String,
    @SerialName("verification_uri_complete") val verificationUriComplete: String? = null,
    @SerialName("expires_in") val expiresIn: Long,
    @SerialName("interval") val interval: Long = 5,
) {
    override fun toString(): String = "JlrDeviceAuthorizationResponse(expiresIn=$expiresIn, interval=$interval)"
}

/** Error body of the auth server, e.g. `authorization_pending` or `use_dpop_nonce`. */
@Serializable
data class JlrErrorResponse(val error: String? = null)
