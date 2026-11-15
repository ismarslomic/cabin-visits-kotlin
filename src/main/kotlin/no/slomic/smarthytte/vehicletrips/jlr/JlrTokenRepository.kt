package no.slomic.smarthytte.vehicletrips.jlr

/** Keeps the refresh token of the JLR login across restarts. The token is a secret: never log it. */
interface JlrTokenRepository {
    suspend fun findRefreshToken(): String?

    suspend fun saveRefreshToken(refreshToken: String)

    suspend fun deleteRefreshToken()
}
