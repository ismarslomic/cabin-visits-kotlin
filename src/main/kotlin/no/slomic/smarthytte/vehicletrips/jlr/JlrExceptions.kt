package no.slomic.smarthytte.vehicletrips.jlr

/**
 * The login to the Data Hub does not work and retrying will not fix it: a rejected or revoked refresh token, a rejected
 * client, a refused or expired device approval. The owner has to act. The message never contains tokens or keys.
 */
class JlrAuthException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/** A request to the Data Hub failed (server error, network, unexpected answer). Retrying later may work. */
class JlrRequestException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
